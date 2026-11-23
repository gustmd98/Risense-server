package com.risense.report;

import com.risense.api.error.ApiException;
import com.risense.checkin.CheckinLifecycle;
import com.risense.checkin.CheckinData.Summary;
import com.risense.domain.member.*;
import com.risense.project.ProjectAccess;
import java.math.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectReportService {
    private final ProjectAccess access;
    private final CheckinLifecycle lifecycle;
    private final JdbcTemplate db;
    private final Clock clock;
    public ProjectReportService(ProjectAccess access, CheckinLifecycle lifecycle, JdbcTemplate db, Clock clock) {
        this.access=access; this.lifecycle=lifecycle; this.db=db; this.clock=clock;
    }
    // Mirrors CheckinHistoryService: exclusions and no-target participants never enter the denominator.
    private static final String STATES = """
        SELECT p.member_id,p.round_id,r.late_until_at,
          CASE WHEN p.excluded_reason IS NOT NULL OR NOT EXISTS(
            SELECT 1 FROM checkin_targets t WHERE t.round_id=p.round_id AND t.member_id=p.member_id AND t.excluded_reason IS NULL)
          THEN 'EXCLUDED' WHEN s.round_id IS NOT NULL THEN CASE WHEN s.is_late THEN 'LATE' ELSE 'ON_TIME' END
          WHEN r.late_until_at<=? THEN 'MISSED' ELSE 'PENDING' END AS state
        FROM checkin_participants p JOIN checkin_rounds r ON r.id=p.round_id
        LEFT JOIN checkin_submissions s ON s.round_id=p.round_id AND s.member_id=p.member_id
        WHERE r.project_id=?
        """;
    @Transactional
    public ReportResponse report(long projectId, long userId, int eventLimit) {
        if(eventLimit<1 || eventLimit>500) throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","eventLimit은 1~500이어야 합니다.");
        var project=access.lockedProject(projectId); access.approvedMember(projectId,userId);
        lifecycle.beforeChange(projectId);
        var now=OffsetDateTime.now(clock);
        var asOf=project.getClosedAt()==null?now:project.getClosedAt();
        var date=asOf.atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate();
        var taskSummary=db.queryForObject("""
            SELECT count(*) FILTER(WHERE status<>'CANCELLED') AS total,
             count(*) FILTER(WHERE status='DONE') AS done,
             count(*) FILTER(WHERE status NOT IN ('DONE','CANCELLED') AND due_date<?) AS overdue,
             count(*) FILTER(WHERE status='CANCELLED') AS cancelled,
             count(*) FILTER(WHERE status='DONE' AND due_date IS NOT NULL
               AND (completed_at AT TIME ZONE 'Asia/Seoul')::date>due_date) AS completed_late
            FROM tasks WHERE project_id=?
            """,(rs,n)->new ReportResponse.TaskSummary(rs.getLong("total"),rs.getLong("done"),rs.getLong("overdue"),rs.getLong("cancelled"),rs.getLong("completed_late")),date,projectId);
        var stateRows=db.query(STATES,(rs,n)->Map.entry(rs.getLong("member_id"),rs.getString("state")),asOf,projectId);
        var byMember=new HashMap<Long,List<String>>();
        stateRows.forEach(row->byMember.computeIfAbsent(row.getKey(),id->new ArrayList<>()).add(row.getValue()));
        var taskCounts=db.query("""
            SELECT a.member_id,
              count(*) FILTER(WHERE t.status='DONE') AS done,
              count(*) FILTER(WHERE t.status NOT IN ('DONE','CANCELLED') AND t.due_date<?) AS overdue
            FROM task_assignees a JOIN tasks t ON t.id=a.task_id
            WHERE t.project_id=? GROUP BY a.member_id
            """,(rs,n)->new long[]{rs.getLong("member_id"),rs.getLong("done"),rs.getLong("overdue")},date,projectId);
        var counts=new HashMap<Long,long[]>();taskCounts.forEach(row->counts.put(row[0],row));
        var members=db.query("""
            SELECT m.*,u.nickname FROM project_members m JOIN users u ON u.id=m.user_id
            WHERE m.project_id=? AND (m.join_status='APPROVED' OR EXISTS(
              SELECT 1 FROM checkin_participants p JOIN checkin_rounds r ON r.id=p.round_id
              WHERE p.member_id=m.id AND r.project_id=m.project_id)
              OR EXISTS(SELECT 1 FROM task_assignee_histories h JOIN tasks t ON t.id=h.task_id
                WHERE h.member_id=m.id AND t.project_id=m.project_id)) ORDER BY m.id
            """,(rs,n)-> {
                long id=rs.getLong("id");var c=counts.getOrDefault(id,new long[3]);
                return new ReportResponse.Member(id,rs.getLong("user_id"),rs.getString("nickname"),
                    MemberRole.valueOf(rs.getString("role")),MemberStatus.valueOf(rs.getString("join_status")),
                    summary(byMember.getOrDefault(id,List.of())),c[1],c[2]);
            },projectId);
        var events=db.query("""
            WITH states AS (
            """+STATES+"""
            ), events AS (
              SELECT 'issue:'||e.id AS id,e.kind AS type,e.occurred_at,t.id AS task_id,t.title AS task_title,
                e.member_id,e.member_id AS actor_id,ARRAY[t.id] AS task_ids,e.content,e.backfilled
              FROM task_issue_events e JOIN tasks t ON t.id=e.task_id WHERE t.project_id=?
              UNION ALL
              SELECT 'cancel:'||t.id,'TASK_CANCELLED',t.cancelled_at,t.id,t.title,
                NULL::bigint,NULL::bigint,ARRAY[t.id],NULL::text,FALSE
              FROM tasks t WHERE t.project_id=? AND t.cancelled_at IS NOT NULL
              UNION ALL
              SELECT 'assignee:'||h.id,'ASSIGNEE_'||h.action,h.changed_at,t.id,t.title,
                h.member_id,h.changed_by,ARRAY[t.id],NULL::text,FALSE
              FROM task_assignee_histories h JOIN tasks t ON t.id=h.task_id WHERE t.project_id=?
              UNION ALL
              SELECT 'missed:'||s.round_id||':'||s.member_id,'CHECKIN_MISSED',s.late_until_at,
                NULL::bigint,NULL::varchar,s.member_id,NULL::bigint,
                ARRAY(SELECT task_id FROM checkin_targets target WHERE target.round_id=s.round_id AND target.member_id=s.member_id AND target.excluded_reason IS NULL ORDER BY task_id),
                NULL::text,FALSE FROM states s WHERE s.state='MISSED'
            )
            SELECT e.*,u.nickname,actor_user.nickname AS actor_nickname FROM events e
            LEFT JOIN project_members m ON m.id=e.member_id LEFT JOIN users u ON u.id=m.user_id
            LEFT JOIN project_members actor ON actor.id=e.actor_id LEFT JOIN users actor_user ON actor_user.id=actor.user_id
            WHERE e.occurred_at<=? ORDER BY e.occurred_at DESC,e.id DESC LIMIT ?
            """,(rs,n)->new ReportResponse.Event(rs.getString("id"),rs.getString("type"),rs.getObject("occurred_at",OffsetDateTime.class),
                (Long)rs.getObject("task_id"),rs.getString("task_title"),(Long)rs.getObject("member_id"),rs.getString("nickname"),
                (Long)rs.getObject("actor_id"),rs.getString("actor_nickname"),ids(rs),rs.getString("content"),rs.getBoolean("backfilled")),
                asOf,projectId,projectId,projectId,projectId,asOf,eventLimit+1);
        var started=db.queryForObject("SELECT issue_events_started_at FROM report_history_metadata WHERE id=1",OffsetDateTime.class);
        return new ReportResponse(new ReportResponse.ProjectInfo(projectId,project.getTitle(),project.getClassName(),project.getProjectType(),
            project.getCreatedAt().atZoneSameInstant(ZoneId.of("Asia/Seoul")).toLocalDate(),project.getDeadline(),project.getStatus(),project.getClosedAt()),
            now,asOf,taskSummary,summary(stateRows.stream().map(Map.Entry::getValue).toList()),members,
            events.stream().limit(eventLimit).toList(),events.size()>eventLimit,started);
    }
    private static List<Long> ids(ResultSet rs) throws SQLException {
        var array=rs.getArray("task_ids");if(array==null)return List.of();
        try { return Arrays.stream((Object[])array.getArray()).map(n->((Number)n).longValue()).toList(); }
        finally { array.free(); }
    }
    static Summary summary(List<String> states) {
        long eligible=states.stream().filter(s->!s.equals("EXCLUDED")).count();
        long onTime=Collections.frequency(states,"ON_TIME"),late=Collections.frequency(states,"LATE");
        var submitted=onTime+late;
        var rate=eligible==0?null:BigDecimal.valueOf(submitted).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(eligible),2,RoundingMode.HALF_UP);
        return new Summary(eligible,submitted,onTime,late,Collections.frequency(states,"MISSED"),Collections.frequency(states,"PENDING"),rate);
    }
}
