package com.risense.checkin;

import com.risense.domain.project.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Caller holds the project's write lock. No dependency on A's write services. */
@Service
@Transactional(propagation=Propagation.MANDATORY)
public class CheckinLifecycle {
    private final CheckinStore store;
    private final Clock clock;
    public CheckinLifecycle(CheckinStore store,Clock clock) { this.store=store; this.clock=clock; }
    private record Schedule(long id, Instant effectiveAt, Instant stopsAt, Set<CheckinDay> days, LocalDate next) {}
    private List<Schedule> schedules(long projectId) {
        return store.jdbc().query("SELECT * FROM checkin_schedules WHERE project_id=? ORDER BY effective_at",(rs,n)->
            new Schedule(rs.getLong("id"),CheckinStore.instant(rs,"effective_at"),CheckinStore.instant(rs,"stops_at"),
                parse(rs.getString("weekdays")),rs.getObject("next_date",LocalDate.class)),projectId);
    }
    private Set<CheckinDay> parse(String value) {
        var result=EnumSet.noneOf(CheckinDay.class);
        for(var day:value.split(",")) result.add(CheckinDay.valueOf(day));
        return result;
    }
    private Set<CheckinDay> days(long projectId) {
        return parse(String.join(",",store.jdbc().queryForList(
            "SELECT day_of_week FROM project_checkin_days WHERE project_id=? ORDER BY day_of_week",String.class,projectId)));
    }
    private static OffsetDateTime time(Instant instant) { return instant.atOffset(ZoneOffset.UTC); }
    public void initialize(long projectId) {
        store.flush();
        if (!schedules(projectId).isEmpty()) return;
        var selected=days(projectId);
        if(selected.isEmpty()) return;
        var start=store.jdbc().queryForObject("SELECT greatest(p.created_at,r.starts_at) FROM projects p CROSS JOIN checkin_rollout r WHERE p.id=?",
            OffsetDateTime.class,projectId).toInstant();
        add(projectId,start,selected);
    }
    private void add(long projectId,Instant start,Set<CheckinDay> selected) {
        var next=CheckinSchedule.firstDateOnOrAfter(start,selected);
        store.jdbc().update("INSERT INTO checkin_schedules(project_id,effective_at,weekdays,next_date) VALUES(?,?,?,?)",
            projectId,time(start),selected.stream().sorted().map(Enum::name).collect(java.util.stream.Collectors.joining(",")),next);
    }
    public void beforeChange(long projectId) {
        store.flush();
        var status=store.jdbc().queryForObject("SELECT status FROM projects WHERE id=?",String.class,projectId);
        if(!"IN_PROGRESS".equals(status)) return;
        initialize(projectId);
        var now=clock.instant();
        for(var schedule:schedules(projectId)) {
            var date=schedule.next();
            while(true) {
                var window=CheckinSchedule.windowFor(date,schedule.days());
                if(window.opensAt().isAfter(now) || (schedule.stopsAt()!=null && !window.opensAt().isBefore(schedule.stopsAt()))) break;
                var ids=store.jdbc().queryForList("""
                    INSERT INTO checkin_rounds(project_id,schedule_id,scheduled_date,opens_at,deadline_at,late_until_at)
                    VALUES(?,?,?,?,?,?) ON CONFLICT(project_id,scheduled_date) DO NOTHING RETURNING id
                    """,Long.class,projectId,schedule.id(),date,time(window.opensAt()),time(window.deadlineAt()),time(window.lateUntilAt()));
                if(!ids.isEmpty()) capture(ids.getFirst(),projectId,window.opensAt());
                date=CheckinSchedule.firstDateOnOrAfter(window.lateUntilAt(),schedule.days());
                store.jdbc().update("UPDATE checkin_schedules SET next_date=? WHERE id=?",date,schedule.id());
            }
        }
    }
    private void capture(long roundId,long projectId,Instant opensAt) {
        // Only obligations: nobody with zero tasks gets a participant row.
        store.jdbc().update("""
            INSERT INTO checkin_participants(round_id,member_id,joined_at)
            SELECT DISTINCT ?,m.id,m.joined_at FROM project_members m
            JOIN task_assignees a ON a.member_id=m.id JOIN tasks t ON t.id=a.task_id
            WHERE m.project_id=? AND t.project_id=? AND m.join_status='APPROVED'
            AND m.joined_at<=? AND a.assigned_at<=? AND t.created_at<=?
            AND t.status NOT IN ('DONE','CANCELLED')
            """,roundId,projectId,projectId,time(opensAt),time(opensAt),time(opensAt));
        store.jdbc().update("""
            INSERT INTO checkin_targets(round_id,member_id,task_id,title_at_open)
            SELECT ?,p.member_id,t.id,t.title FROM checkin_participants p
            JOIN task_assignees a ON a.member_id=p.member_id JOIN tasks t ON t.id=a.task_id
            WHERE p.round_id=? AND t.project_id=? AND a.assigned_at<=? AND t.created_at<=?
            AND t.status NOT IN ('DONE','CANCELLED')
            """,roundId,roundId,projectId,time(opensAt),time(opensAt));
    }
    public void changeSchedule(long projectId,Collection<CheckinDay> selected) {
        store.flush();
        var all=schedules(projectId);
        var wanted=Set.copyOf(selected);
        if(!all.isEmpty() && all.getLast().days().equals(wanted)) return;
        var now=clock.instant();
        var start=store.latest(projectId).map(r->r.lateUntilAt().isAfter(now)?r.lateUntilAt():now).orElse(now);
        // Replace a not-yet-effective configuration; preserve all started schedule versions.
        store.jdbc().update("DELETE FROM checkin_schedules WHERE project_id=? AND effective_at>?",projectId,time(now));
        store.jdbc().update("DELETE FROM checkin_schedules s WHERE project_id=? AND effective_at=? AND NOT EXISTS(SELECT 1 FROM checkin_rounds r WHERE r.schedule_id=s.id)",projectId,time(start));
        store.jdbc().update("UPDATE checkin_schedules SET stops_at=? WHERE project_id=? AND (stops_at IS NULL OR stops_at>?)",
            time(start),projectId,time(start));
        add(projectId,start,wanted);
    }
    public void unassigned(long projectId,long taskId,long memberId) {
        store.jdbc().update("""
            UPDATE checkin_targets t SET excluded_reason='UNASSIGNED' FROM checkin_rounds r,checkin_participants p
            WHERE t.round_id=r.id AND p.round_id=t.round_id AND p.member_id=t.member_id
            AND r.project_id=? AND r.late_until_at>? AND t.task_id=? AND t.member_id=?
            AND p.departed_at IS NULL AND t.excluded_reason IS NULL
            """,projectId,time(clock.instant()),taskId,memberId);
    }
    public void cancelled(long projectId,long taskId) {
        store.jdbc().update("""
            UPDATE checkin_targets t SET excluded_reason='CANCELLED' FROM checkin_rounds r,checkin_participants p
            WHERE t.round_id=r.id AND p.round_id=t.round_id AND p.member_id=t.member_id
            AND r.project_id=? AND r.late_until_at>? AND t.task_id=?
            AND p.departed_at IS NULL AND t.excluded_reason IS NULL
            """,projectId,time(clock.instant()),taskId);
    }
    public void departed(long projectId,long memberId) {
        beforeChange(projectId);
        store.jdbc().update("""
            UPDATE checkin_participants p SET departed_at=? FROM checkin_rounds r
            WHERE p.round_id=r.id AND r.project_id=? AND r.late_until_at>? AND p.member_id=? AND p.departed_at IS NULL
            """,time(clock.instant()),projectId,time(clock.instant()),memberId);
    }
    public void closed(long projectId) {
        store.jdbc().update("""
            UPDATE checkin_participants p SET excluded_reason='PROJECT_CLOSED' FROM checkin_rounds r
            WHERE p.round_id=r.id AND r.project_id=? AND r.late_until_at>?
            AND NOT EXISTS(SELECT 1 FROM checkin_submissions s WHERE s.round_id=p.round_id AND s.member_id=p.member_id)
            """,projectId,time(clock.instant()));
    }
}
