package com.risense.checkin;

import static com.risense.checkin.CheckinData.*;
import com.risense.api.error.ApiException;
import com.risense.project.ProjectAccess;
import java.math.*;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CheckinHistoryService {
    private final ProjectAccess access;
    private final CheckinLifecycle lifecycle;
    private final CheckinStore store;
    private final Clock clock;
    public CheckinHistoryService(ProjectAccess access,CheckinLifecycle lifecycle,CheckinStore store,Clock clock) {
        this.access=access;this.lifecycle=lifecycle;this.store=store;this.clock=clock;
    }
    public Status status(long projectId,long roundId,long userId) {
        access.lockedProject(projectId);access.approvedMember(projectId,userId);lifecycle.beforeChange(projectId);
        var round=store.round(projectId,roundId).orElseThrow(()->new ApiException(HttpStatus.NOT_FOUND,"CHECKIN_ROUND_NOT_FOUND","회차를 찾을 수 없습니다."));
        var now=clock.instant();
        var members=store.jdbc().query("""
            SELECT p.*,u.nickname,
            (SELECT count(*) FROM checkin_targets t WHERE t.round_id=p.round_id AND t.member_id=p.member_id AND t.excluded_reason IS NULL) AS targets
            FROM checkin_participants p JOIN project_members m ON m.id=p.member_id JOIN users u ON u.id=m.user_id
            WHERE p.round_id=? ORDER BY p.member_id
            """,(rs,n)-> {
                long memberId=rs.getLong("member_id"); int count=rs.getInt("targets");
                var submission=store.submission(roundId,memberId);
                String state=state(count,rs.getString("excluded_reason"),submission,round,now);
                return new MemberStatus(memberId,rs.getString("nickname"),state,rs.getObject("departed_at")!=null,count,submission);
            },roundId);
        // No new display category for obligation-free members.
        var visible=members.stream().filter(m->!m.status().equals("EXCLUDED")).toList();
        return new Status(round,now,summary(visible.stream().map(MemberStatus::status).toList()),visible);
    }
    public HistoryPage mine(long projectId,long userId,Long beforeRoundId,int limit) {
        access.lockedProject(projectId);var member=access.approvedMember(projectId,userId);lifecycle.beforeChange(projectId);
        if(limit<1 || limit>100 || (beforeRoundId!=null && beforeRoundId<=0))
            throw new ApiException(HttpStatus.BAD_REQUEST,"INVALID_REQUEST","limit은 1~100, 커서는 양수여야 합니다.");
        var rounds=store.jdbc().query("""
            SELECT r.* FROM checkin_rounds r JOIN checkin_participants p ON p.round_id=r.id
            WHERE r.project_id=? AND p.member_id=? AND r.id<? ORDER BY r.id DESC LIMIT ?
            """,CheckinStore.ROUND,projectId,member.getId(),beforeRoundId==null?Long.MAX_VALUE:beforeRoundId,limit+1);
        var items=rounds.stream().limit(limit).map(round-> {
            int count=store.jdbc().queryForObject("SELECT count(*) FROM checkin_targets WHERE round_id=? AND member_id=? AND excluded_reason IS NULL",Integer.class,round.id(),member.getId());
            var exclusion=store.jdbc().queryForObject("SELECT coalesce(excluded_reason,'') FROM checkin_participants WHERE round_id=? AND member_id=?",String.class,round.id(),member.getId());
            var submitted=store.submission(round.id(),member.getId());
            return new History(round,state(count,exclusion.isEmpty()?null:exclusion,submitted,round,clock.instant()),count,submitted);
        }).toList();
        return new HistoryPage(items,rounds.size()>limit?items.getLast().round().id():null);
    }
    public Summary aggregate(long projectId,long userId) {
        access.lockedProject(projectId);access.approvedMember(projectId,userId);lifecycle.beforeChange(projectId);
        var states=store.jdbc().query("""
            SELECT CASE WHEN p.excluded_reason IS NOT NULL OR NOT EXISTS(
              SELECT 1 FROM checkin_targets t WHERE t.round_id=p.round_id AND t.member_id=p.member_id AND t.excluded_reason IS NULL)
              THEN 'EXCLUDED' WHEN s.round_id IS NOT NULL THEN CASE WHEN s.is_late THEN 'LATE' ELSE 'ON_TIME' END
              WHEN r.late_until_at<=? THEN 'MISSED' ELSE 'PENDING' END AS state
            FROM checkin_participants p JOIN checkin_rounds r ON r.id=p.round_id
            LEFT JOIN checkin_submissions s ON s.round_id=p.round_id AND s.member_id=p.member_id
            WHERE r.project_id=?
            """,(rs,n)->rs.getString(1),clock.instant().atOffset(ZoneOffset.UTC),projectId);
        return summary(states);
    }
    static String state(int count,String excluded,Submission submission,Round round,Instant now) {
        if(count==0 || excluded!=null) return "EXCLUDED";
        if(submission!=null) return submission.late()?"LATE":"ON_TIME";
        return now.isBefore(round.lateUntilAt())?"PENDING":"MISSED";
    }
    static Summary summary(List<String> states) {
        long eligible=states.stream().filter(s->!s.equals("EXCLUDED")).count();
        long onTime=Collections.frequency(states,"ON_TIME"),late=Collections.frequency(states,"LATE");
        long submitted=onTime+late;
        var rate=eligible==0?null:BigDecimal.valueOf(submitted).multiply(BigDecimal.valueOf(100))
            .divide(BigDecimal.valueOf(eligible),2,RoundingMode.HALF_UP);
        return new Summary(eligible,submitted,onTime,late,Collections.frequency(states,"MISSED"),Collections.frequency(states,"PENDING"),rate);
    }
}
