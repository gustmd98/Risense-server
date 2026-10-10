package com.risense.checkin;

import static com.risense.checkin.CheckinData.*;
import static org.assertj.core.api.Assertions.*;
import com.risense.api.error.ApiException;
import com.risense.domain.project.*;
import com.risense.project.*;
import com.risense.taskapi.*;
import com.risense.team.TeamService;
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

/** Opt-in PostgreSQL integration suite. Only use a disposable DB. */
@SpringBootTest(properties={"app.checkin.scheduler-enabled=false"})
@Import(CheckinDatabaseTest.TestConfig.class)
@EnabledIfEnvironmentVariable(named="CHECKIN_TEST_DB",matches="disposable")
@Transactional
class CheckinDatabaseTest {
    static class TestClock extends Clock {
        Instant now=Instant.parse("2026-10-07T14:00:00Z");
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return now; }
    }
    @TestConfiguration static class TestConfig {
        @Bean @Primary TestClock testClock() { return new TestClock(); }
    }
    @Autowired TestClock clock;
    @Autowired JdbcTemplate db;
    @Autowired CheckinService checkins;
    @Autowired CheckinLifecycle lifecycle;
    @Autowired CheckinHistoryService history;
    @Autowired ProjectAccess access;
    @Autowired TaskService tasks;
    @Autowired TaskRelationService relations;
    @Autowired TeamService team;
    @Autowired ProjectService projects;
    @Autowired com.risense.issue.TaskIssueService issues;
    long project,user,member,task,secondUser,secondMember;
    final Instant opens=Instant.parse("2026-10-07T15:00:00Z");
    @BeforeEach void fixture() {
        clock.now=opens.minusSeconds(3600);
        db.update("UPDATE checkin_rollout SET starts_at=?",time(clock.now));
        user=user("leader");secondUser=user("member");
        project=db.queryForObject("""
            INSERT INTO projects(title,deadline,checkin_time,checkin_frequency,status,created_by,created_at,updated_at)
            VALUES('test','2026-12-20','23:00',2,'IN_PROGRESS',?,?,?) RETURNING id
            """,Long.class,user,time(clock.now),time(clock.now));
        db.update("INSERT INTO project_checkin_days VALUES(?,'MON'),(?,'THU')",project,project);
        member=member(user,"LEADER");secondMember=member(secondUser,"MEMBER");
        task=task("first");assign(task,member);assign(task,secondMember);
        access.lockedProject(project);lifecycle.initialize(project);
        clock.now=opens;
    }
    long user(String nickname) {
        return db.queryForObject("INSERT INTO users(email,password_hash,nickname,created_at) VALUES(?,'unused',?,?) RETURNING id",
            Long.class,UUID.randomUUID()+"@example.com",nickname,time(clock.now));
    }
    long member(long id,String role) {
        return db.queryForObject("""
            INSERT INTO project_members(project_id,user_id,role,join_status,requested_at,joined_at)
            VALUES(?,?,?,'APPROVED',?,?) RETURNING id
            """,Long.class,project,id,role,time(clock.now),time(clock.now));
    }
    long task(String title) {
        return db.queryForObject("""
            INSERT INTO tasks(project_id,title,size,status,progress,sort_order,created_at,updated_at)
            VALUES(?,?,'S','TODO',0,0,?,?) RETURNING id
            """,Long.class,project,title,time(clock.now),time(clock.now));
    }
    void assign(long task,long member) { db.update("INSERT INTO task_assignees VALUES(?,?,?)",task,member,time(clock.now)); }
    Submit request(Current current,long revision,String issue) {
        return new Submit(UUID.randomUUID(),revision,current.targets().stream().filter(t->t.excludedReason()==null)
            .map(t->new Entry(t.taskId(),t.version(),issue,t.current().issue().revision(),"request","next")).toList());
    }
    void conflict(Runnable action,String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.getCode()).isEqualTo(code));
    }
    @Test void roundGenerationIsIdempotentAndSubmissionReplays() {
        var current=checkins.current(project,user);assertThat(current.canSubmit()).isTrue();
        lifecycle.beforeChange(project);lifecycle.beforeChange(project);
        assertThat(db.queryForObject("SELECT count(*) FROM checkin_rounds WHERE project_id=?",Long.class,project)).isEqualTo(1);
        var request=request(current,0,null);
        var first=checkins.submit(project,current.round().id(),user,request,false);
        assertThat(checkins.submit(project,current.round().id(),user,request,false)).isEqualTo(first);
        assertThat(checkins.detail(project,current.round().id(),user)).isEqualTo(first);
        assertThat(history.aggregate(project,user).submitted()).isEqualTo(1);
    }
    @Test void staleTaskVersionNeverOverwritesSharedChanges() {
        var current=checkins.current(project,user);var request=request(current,0,null);
        db.update("UPDATE tasks SET title='changed' WHERE id=?",task);
        conflict(()->checkins.submit(project,current.round().id(),user,request,false),"CHECKIN_TASK_CHANGED");
        assertThat(db.queryForObject("SELECT count(*) FROM checkin_submissions WHERE round_id=?",Long.class,current.round().id())).isZero();
    }
    @Test void issueReportAndSnapshotAreAtomicAndResolutionPreservesHistory() {
        var current=checkins.current(project,user);
        var submitted=checkins.submit(project,current.round().id(),user,request(current,0,"blocked by dependency"),false);
        assertThat(submitted.tasks().getFirst().issue().open()).isTrue();
        assertThat(submitted.tasks().getFirst().issue().revision()).isEqualTo(1);
        issues.resolve(project,task,user,1);
        assertThat(checkins.current(project,user).targets().getFirst().current().issue().open()).isFalse();
        assertThat(checkins.detail(project,current.round().id(),user)).isEqualTo(submitted);
    }
    @Test void onTimeEditPreservesFirstTimestampAndLateEditIsDenied() {
        var current=checkins.current(project,user);
        var first=checkins.submit(project,current.round().id(),user,request(current,0,null),false);
        clock.now=opens.plusSeconds(60);
        var edit=checkins.submit(project,current.round().id(),user,request(checkins.current(project,user),1,null),true);
        assertThat(edit.submittedAt()).isEqualTo(first.submittedAt());assertThat(edit.revision()).isEqualTo(2);
        clock.now=current.round().deadlineAt();
        conflict(()->checkins.submit(project,current.round().id(),user,request(checkins.current(project,user),2,null),true),"CHECKIN_LATE_EDIT_NOT_ALLOWED");
        var late=checkins.submit(project,current.round().id(),secondUser,request(checkins.current(project,secondUser),0,null),false);
        assertThat(late.late()).isTrue();
    }
    @Test void unassignedTargetIsExcludedButSubmissionRemains() {
        var current=checkins.current(project,user);
        var first=checkins.submit(project,current.round().id(),user,request(current,0,null),false);
        tasks.setAssignees(project,task,user,new AssigneeRequest(List.of(secondMember)));
        assertThat(checkins.current(project,user).canSubmit()).isFalse();
        assertThat(checkins.detail(project,current.round().id(),user)).isEqualTo(first);
        assertThat(history.aggregate(project,user).eligible()).isEqualTo(1);
        assertThat(history.aggregate(project,user).submitted()).isZero();
    }
    @Test void removalKeepsObligationAndRejoinCannotRestoreSubmissionRight() {
        var round=checkins.current(project,user).round();
        team.remove(project,secondMember,user);
        assertThat(history.status(project,round.id(),user).summary().eligible()).isEqualTo(2);
        clock.now=opens.plusSeconds(60);
        var invite=team.issue(project,user,new com.risense.team.TeamRequests.Invite(168));
        team.join(invite.token(),secondUser);
        team.approve(project,secondMember,user);
        assertThat(checkins.current(project,secondUser).unavailableReason()).isEqualTo("CHECKIN_NOT_ELIGIBLE");
        clock.now=round.lateUntilAt();
        assertThat(history.status(project,round.id(),user).summary().missed()).isEqualTo(2);
    }
    @Test void completedDuringRoundRemainsAndNextRoundExcludesIt() {
        var child=relations.createChild(project,task,user,new RelationRequests.SubTaskSettings("child",secondMember,null));
        relations.completeChild(project,task,child.id(),secondUser,new RelationRequests.Completion(true));
        var current=checkins.current(project,user);
        assertThat(current.targets()).hasSize(1);assertThat(current.targets().getFirst().current().status()).isEqualTo("DONE");
        clock.now=current.round().lateUntilAt();
        assertThat(checkins.current(project,user).targets()).isEmpty();
    }
    @Test void closureExcludesPendingButPreservesSubmittedAndAlreadyMissed() {
        var current=checkins.current(project,user);
        checkins.submit(project,current.round().id(),user,request(current,0,null),false);
        projects.close(project,user);
        var summary=history.status(project,current.round().id(),user).summary();
        assertThat(summary.eligible()).isEqualTo(1);assertThat(summary.submitted()).isEqualTo(1);assertThat(summary.missed()).isZero();
    }
    @Test void cancellationExcludesCurrentTargetsAndZeroDenominatorIsNull() {
        var current=checkins.current(project,user);tasks.cancel(project,task,user);
        assertThat(history.status(project,current.round().id(),user).members()).isEmpty();
        assertThat(history.aggregate(project,user).submissionRate()).isNull();
    }
    @Test void scheduleChangePreservesExistingCutoff() {
        var old=checkins.current(project,user).round();clock.now=opens.plusSeconds(60);
        projects.update(project,user,new ProjectRequest("test",null,LocalDate.parse("2026-12-20"),LocalTime.of(23,0),1,List.of(CheckinDay.FRI)));
        assertThat(checkins.current(project,user).round()).isEqualTo(old);
        clock.now=old.lateUntilAt();
        assertThat(checkins.current(project,user).round()).isEqualTo(old);
        clock.now=Instant.parse("2026-10-15T15:00:00Z");
        assertThat(checkins.current(project,user).round().scheduledDate()).isEqualTo(LocalDate.parse("2026-10-16"));
    }
    @Test void renameBeforeDelayedGenerationPreservesOriginalTargetTitle() {
        tasks.update(project,task,user,new TaskRequest("renamed",com.risense.domain.task.TaskSize.S,null,null));
        var current=checkins.current(project,user);
        assertThat(current.targets().getFirst().titleAtOpen()).isEqualTo("first");
        assertThat(current.targets().getFirst().current().title()).isEqualTo("renamed");
    }
    @Test void replacingPendingScheduleChangeKeepsOnlyLatestFutureVersion() {
        var old=checkins.current(project,user).round();clock.now=opens.plusSeconds(60);
        projects.update(project,user,new ProjectRequest("test",null,LocalDate.parse("2026-12-20"),LocalTime.of(23,0),1,List.of(CheckinDay.FRI)));
        clock.now=opens.plusSeconds(120);
        projects.update(project,user,new ProjectRequest("test",null,LocalDate.parse("2026-12-20"),LocalTime.of(23,0),1,List.of(CheckinDay.TUE)));
        assertThat(db.queryForObject("SELECT count(*) FROM checkin_schedules WHERE project_id=?",Long.class,project)).isEqualTo(2);
        assertThat(checkins.current(project,user).round()).isEqualTo(old);
        clock.now=Instant.parse("2026-10-12T15:00:00Z");
        assertThat(checkins.current(project,user).round().scheduledDate()).isEqualTo(LocalDate.parse("2026-10-13"));
    }
    @Test void closurePreservesAlreadyFinalMissedRound() {
        var old=checkins.current(project,user).round();clock.now=old.lateUntilAt();
        projects.close(project,user);
        var previous=history.status(project,old.id(),user).summary();
        assertThat(previous.eligible()).isEqualTo(2);assertThat(previous.missed()).isEqualTo(2);
        assertThat(history.aggregate(project,user).missed()).isEqualTo(2);
    }
    @Test void boundaryRejectsOldRoundAtExactLateCutoff() {
        var current=checkins.current(project,user);var request=request(current,0,null);
        clock.now=current.round().lateUntilAt();
        conflict(()->checkins.submit(project,current.round().id(),user,request,false),"CHECKIN_CLOSED");
        assertThat(history.status(project,current.round().id(),user).summary().missed()).isEqualTo(2);
    }
    @Test void pendingIsNotMissedBeforeFinalCutoff() {
        var current=checkins.current(project,user);clock.now=current.round().deadlineAt();
        var summary=history.status(project,current.round().id(),user).summary();
        assertThat(summary.missed()).isZero();assertThat(summary.pending()).isEqualTo(2);
    }
    @Test void newlyAssignedTaskWaitsUntilNextRound() {
        var current=checkins.current(project,user);clock.now=opens.plusSeconds(60);
        long newTask=task("new");tasks.setAssignees(project,newTask,user,new AssigneeRequest(List.of(member)));
        assertThat(checkins.current(project,user).targets()).extracting(Target::taskId).containsExactly(task);
        clock.now=current.round().lateUntilAt();
        assertThat(checkins.current(project,user).targets()).extracting(Target::taskId).containsExactlyInAnyOrder(task,newTask);
    }
    @Test void wrongProjectAndNonmemberAreDenied() {
        var current=checkins.current(project,user);long outsider=user("outsider");
        conflict(()->checkins.current(project,outsider),"PROJECT_ACCESS_DENIED");
        conflict(()->checkins.detail(project+99999,current.round().id(),user),"PROJECT_NOT_FOUND");
        conflict(()->checkins.detail(project,current.round().id()+99999,user),"CHECKIN_ROUND_NOT_FOUND");
    }
    @Test void duplicateAndMissingTargetsNeverCreatePartialSubmission() {
        var current=checkins.current(project,user);var valid=request(current,0,null);var entry=valid.tasks().getFirst();
        conflict(()->checkins.submit(project,current.round().id(),user,new Submit(UUID.randomUUID(),0L,List.of(entry,entry)),false),"CHECKIN_INVALID_TASK_IDS");
        conflict(()->checkins.submit(project,current.round().id(),user,new Submit(UUID.randomUUID(),0L,List.of()),false),"CHECKIN_TARGETS_CHANGED");
        assertThat(db.queryForObject("SELECT count(*) FROM checkin_submissions WHERE round_id=?",Long.class,current.round().id())).isZero();
    }
    @Test void requestKeyReuseAndStaleSubmissionRevisionAreRejected() {
        var current=checkins.current(project,user);var firstRequest=request(current,0,null);
        checkins.submit(project,current.round().id(),user,firstRequest,false);
        var different=new Submit(firstRequest.requestKey(),0L,List.of(new Entry(task,firstRequest.tasks().getFirst().expectedVersion(),null,0,null,"different")));
        conflict(()->checkins.submit(project,current.round().id(),user,different,false),"CHECKIN_REQUEST_KEY_REUSED");
        conflict(()->checkins.submit(project,current.round().id(),user,request(checkins.current(project,user),0,null),true),"CHECKIN_VERSION_CONFLICT");
    }
    @Test void sharedIssueResolutionMakesOtherEditorsVersionStale() {
        var current=checkins.current(project,user);checkins.submit(project,current.round().id(),user,request(current,0,"issue"),false);
        var other=checkins.current(project,secondUser);var stale=request(other,0,"still open");
        issues.resolve(project,task,user,1);
        conflict(()->checkins.submit(project,current.round().id(),secondUser,stale,false),"CHECKIN_TASK_CHANGED");
        assertThat(checkins.current(project,user).targets().getFirst().current().issue().open()).isFalse();
    }
    @Test void concurrentReplayUsesOneSubmissionAndOneRequestRecord() throws Exception {
        var current=checkins.current(project,user);var request=request(current,0,null);long round=current.round().id();
        org.springframework.test.context.transaction.TestTransaction.flagForCommit();
        org.springframework.test.context.transaction.TestTransaction.end();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var first=executor.submit(()->{start.await();return checkins.submit(project,round,user,request,false);});
            var second=executor.submit(()->{start.await();return checkins.submit(project,round,user,request,false);});
            start.countDown();assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS))
                .isEqualTo(second.get(10,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(db.queryForObject("SELECT count(*) FROM checkin_submissions WHERE round_id=?",Long.class,round)).isEqualTo(1);
            assertThat(db.queryForObject("SELECT count(*) FROM checkin_requests WHERE round_id=?",Long.class,round)).isEqualTo(1);
        } finally {
            for(var table:List.of("checkin_requests","checkin_submissions","checkin_targets","checkin_participants"))
                db.update("DELETE FROM "+table+" WHERE round_id IN(SELECT id FROM checkin_rounds WHERE project_id=?)",project);
            db.update("DELETE FROM checkin_rounds WHERE project_id=?",project);
            db.update("DELETE FROM checkin_schedules WHERE project_id=?",project);
            db.update("DELETE FROM task_assignees WHERE task_id=?",task);
            db.update("DELETE FROM tasks WHERE id=?",task);
            db.update("DELETE FROM project_checkin_days WHERE project_id=?",project);
            db.update("DELETE FROM project_members WHERE project_id=?",project);
            db.update("DELETE FROM projects WHERE id=?",project);
            db.update("DELETE FROM users WHERE id IN (?,?)",user,secondUser);
        }
    }
    private static OffsetDateTime time(Instant value) { return value.atOffset(ZoneOffset.UTC); }
}
