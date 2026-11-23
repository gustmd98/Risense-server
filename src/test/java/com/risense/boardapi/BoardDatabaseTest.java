package com.risense.boardapi;

import static org.assertj.core.api.Assertions.*;
import com.risense.api.error.ApiException;
import com.risense.checkin.CheckinHistoryService;
import com.risense.domain.task.TaskSize;
import com.risense.project.*;
import com.risense.report.*;
import com.risense.taskapi.*;
import com.risense.issue.TaskIssueService;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties={"app.checkin.scheduler-enabled=false"})
@Import(BoardDatabaseTest.Config.class)
@EnabledIfEnvironmentVariable(named="BOARD_TEST_DB",matches="disposable")
@Transactional
class BoardDatabaseTest {
    static class TestClock extends Clock {
        Instant now=Instant.parse("2026-10-08T03:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return Clock.fixed(now,zone);}
        public Instant instant(){return now;}
    }
    @TestConfiguration static class Config { @Bean @Primary TestClock boardClock(){return new TestClock();} }
    @Autowired TestClock clock;
    @Autowired JdbcTemplate db;
    @Autowired TaskService tasks;
    @Autowired TaskTemplateService templates;
    @Autowired TaskRelationService relations;
    @Autowired ProjectReportService reports;
    @Autowired ProjectService projects;
    @Autowired CheckinHistoryService history;
    @Autowired TaskIssueService issues;
    @Autowired EntityManager em;
    long project,user,member,otherUser,otherMember,task;
    @BeforeEach void fixture() {
        clock.now=Instant.parse("2026-10-08T03:00:00Z");
        user=user("leader");otherUser=user("member");
        project=db.queryForObject("""
            INSERT INTO projects(title,class_name,deadline,checkin_time,checkin_frequency,status,created_by,created_at,updated_at)
            VALUES('board','class','2026-12-20','23:00',1,'IN_PROGRESS',?,?,?) RETURNING id
            """,Long.class,user,now(),now());
        db.update("INSERT INTO project_checkin_days VALUES(?,'THU')",project);
        member=member(user,"LEADER");otherMember=member(otherUser,"MEMBER");
        task=tasks.create(project,user,new TaskRequest("task",TaskSize.M,LocalDate.of(2026,12,20),null)).id();
        tasks.setAssignees(project,task,user,new AssigneeRequest(List.of(member,otherMember)));
    }
    OffsetDateTime now(){return clock.instant().atOffset(ZoneOffset.UTC);}
    long user(String name){return db.queryForObject("INSERT INTO users(email,password_hash,nickname,created_at) VALUES(?,'hash',?,?) RETURNING id",Long.class,UUID.randomUUID()+"@example.com",name,now());}
    long member(long uid,String role){return db.queryForObject("INSERT INTO project_members(project_id,user_id,role,join_status,requested_at,joined_at) VALUES(?,?,?,'APPROVED',?,?) RETURNING id",Long.class,project,uid,role,now(),now());}
    void conflict(Runnable action,String code){assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo(code));}
    @Test void scheduleRoundTripUsesRealVersionAndMetadataOmissionPreservesStart() {
        var before=tasks.detail(project,task,user);
        var changed=tasks.schedule(project,task,user,new TaskScheduleRequest(LocalDate.of(2026,12,1),LocalDate.of(2026,12,10),before.revision()));
        assertThat(changed.revision()).isGreaterThan(before.revision());
        em.clear();
        assertThat(tasks.detail(project,task,otherUser).startDate()).isEqualTo(LocalDate.of(2026,12,1));
        conflict(()->tasks.schedule(project,task,user,new TaskScheduleRequest(null,null,before.revision())),"TASK_VERSION_CONFLICT");
        var edited=tasks.update(project,task,user,new TaskRequest("renamed",TaskSize.L,LocalDate.of(2026,12,12),null));
        assertThat(edited.startDate()).isEqualTo(LocalDate.of(2026,12,1));
        var cleared=tasks.schedule(project,task,user,new TaskScheduleRequest(null,null,edited.revision()));
        assertThat(cleared.startDate()).isNull();assertThat(cleared.dueDate()).isNull();
    }
    @Test void templatePersistsAllDatesAndEdgesAndInvalidPlanAddsNothing() {
        long before=db.queryForObject("SELECT count(*) FROM tasks WHERE project_id=?",Long.class,project);
        var a=new TaskTemplateRequest.Selection("presentation-outline",LocalDate.of(2026,12,1),LocalDate.of(2026,12,2),List.of());
        var b=new TaskTemplateRequest.Selection("presentation-slides",LocalDate.of(2026,12,3),LocalDate.of(2026,12,5),List.of("presentation-outline"));
        var result=templates.create(project,user,new TaskTemplateRequest(TaskTemplate.PRESENTATION,null,List.of(a,b)));
        em.clear();
        assertThat(result).hasSize(2);assertThat(tasks.detail(project,result.get(1).id(),user).startDate()).isEqualTo(b.startDate());
        assertThat(relations.dependencies(project,user)).containsExactly(new TaskRelationService.Dependency(result.get(1).id(),result.get(0).id()));
        var bad=new TaskTemplateRequest.Selection("presentation-slides",LocalDate.of(2026,12,6),LocalDate.of(2026,12,5),List.of());
        conflict(()->templates.create(project,user,new TaskTemplateRequest(TaskTemplate.PRESENTATION,null,List.of(a,bad))),"INVALID_TASK_DATES");
        assertThat(db.queryForObject("SELECT count(*) FROM tasks WHERE project_id=?",Long.class,project)).isEqualTo(before+2);
    }
    @Test void projectTypeIsPersistentAndOmittedTypeDoesNotEraseIt() {
        projects.update(project,user,new ProjectRequest("board",null,LocalDate.of(2026,12,20),LocalTime.of(23,0),1,List.of(com.risense.domain.project.CheckinDay.THU),"development"));
        em.clear();assertThat(projects.detail(project,otherUser).projectType()).isEqualTo("development");
        projects.update(project,user,new ProjectRequest("board2",null,LocalDate.of(2026,12,20),LocalTime.of(23,0),1,List.of(com.risense.domain.project.CheckinDay.THU)));
        assertThat(projects.detail(project,user).projectType()).isEqualTo("development");
    }
    long round(String date,String state,long mid) {
        long schedule=db.queryForObject("SELECT id FROM checkin_schedules WHERE project_id=? ORDER BY id LIMIT 1",Long.class,project);
        var day=LocalDate.parse(date);var opens=day.atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        long id=db.queryForObject("INSERT INTO checkin_rounds(project_id,schedule_id,scheduled_date,opens_at,deadline_at,late_until_at) VALUES(?,?,?,?,?,?) RETURNING id",Long.class,project,schedule,day,opens,opens.plusDays(1),opens.plusDays(1));
        db.update("INSERT INTO checkin_participants(round_id,member_id,joined_at,excluded_reason) VALUES(?,?,?,?)",id,mid,now(),state.equals("EXCLUDED")?"PROJECT_CLOSED":null);
        db.update("INSERT INTO checkin_targets(round_id,member_id,task_id,title_at_open) VALUES(?,?,?,'task')",id,mid,task);
        if(state.equals("ON_TIME")||state.equals("LATE")) db.update("INSERT INTO checkin_submissions VALUES(?,?,1,?,?,?,'[]'::jsonb)",id,mid,state.equals("LATE"),opens.plusHours(1),opens.plusHours(1));
        return id;
    }
    @Test void reportMatchesCheckinPolicyIncludesLateAndExcludesNoObligationAndPendingFromMissed() {
        round("2026-10-04","ON_TIME",member);round("2026-10-05","LATE",otherMember);
        round("2026-10-06","MISSED",otherMember);round("2026-10-07","EXCLUDED",member);
        round("2026-10-08","PENDING",otherMember);
        var report=reports.report(project,user,100);
        assertThat(report.checkins()).isEqualTo(history.aggregate(project,user));
        assertThat(report.checkins().eligible()).isEqualTo(4);assertThat(report.checkins().submitted()).isEqualTo(2);
        assertThat(report.checkins().submissionRate()).isEqualByComparingTo("50.00");
        assertThat(report.checkins().pending()).isEqualTo(1);assertThat(report.checkins().missed()).isEqualTo(1);
        assertThat(report.members().stream().mapToLong(m->m.checkins().eligible()).sum()).isEqualTo(4);
        assertThat(report.events().stream().filter(e->e.type().equals("CHECKIN_MISSED"))).hasSize(1);
        assertThat(report.events().stream().filter(e->e.type().equals("CHECKIN_MISSED")).findFirst().orElseThrow().relatedTaskIds()).containsExactly(task);
    }
    @Test void reportKeepsRepeatedIssueCyclesAndSharesCompletedTaskCountsWithoutDoubleCountingProject() {
        issues.report(project,task,user,"first",0);clock.now=clock.now.plusSeconds(1);
        issues.resolve(project,task,user,1);clock.now=clock.now.plusSeconds(1);
        issues.report(project,task,otherUser,"second",2);clock.now=clock.now.plusSeconds(1);
        issues.resolve(project,task,otherUser,3);
        db.update("UPDATE tasks SET status='DONE',progress=100,due_date='2026-10-07',completed_at=? WHERE id=?",now(),task);
        em.clear();var report=reports.report(project,user,100);
        assertThat(report.tasks().totalTasks()).isEqualTo(1);assertThat(report.tasks().completedTasks()).isEqualTo(1);
        assertThat(report.tasks().completedLateTasks()).isEqualTo(1);
        assertThat(report.members()).allSatisfy(m->assertThat(m.completedTasks()).isEqualTo(1));
        assertThat(report.events().stream().filter(e->e.type().startsWith("ISSUE_"))).hasSize(4);
        assertThat(report.events().stream().filter(e->e.type().equals("ISSUE_REPORTED")).map(ReportResponse.Event::content)).containsExactly("second","first");
        assertThat(report.checkins().submissionRate()).isNull();
        assertThat(reports.report(project,user,1).eventsTruncated()).isTrue();
    }
    @Test void closedReportFreezesDeadlineJudgementAndRejectsOutsiders() {
        db.update("UPDATE tasks SET due_date='2026-10-09' WHERE id=?",task);em.clear();
        projects.close(project,user);clock.now=clock.now.plusSeconds(86400*5);
        var report=reports.report(project,user,100);
        assertThat(report.tasks().overdueTasks()).isZero();
        assertThat(report.asOf()).isEqualTo(projects.detail(project,user).closedAt());
        conflict(()->reports.report(project,user("outsider"),100),"PROJECT_ACCESS_DENIED");
        conflict(()->reports.report(project,user,0),"INVALID_REQUEST");
    }
}
