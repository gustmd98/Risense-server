package com.risense.issue;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.risense.api.error.ApiException;
import com.risense.domain.member.*;
import com.risense.domain.project.*;
import com.risense.domain.task.*;
import com.risense.domain.user.User;
import com.risense.project.ProjectAccess;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskIssueServiceTest {
    private final ProjectAccess access = mock(ProjectAccess.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskAssigneeRepository assignments = mock(TaskAssigneeRepository.class);
    private final TaskIssueRepository issues = mock(TaskIssueRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);
    private final TaskIssueService service = new TaskIssueService(access, tasks, assignments, issues, clock);
    private Project project;
    private ProjectMember member;
    private Task task;

    @BeforeEach void setup() {
        var now = OffsetDateTime.now(clock);
        var user = User.register("a@example.com", "hash", "a", now); user.setId(1L);
        project = Project.create(user, now); project.setId(1L);
        member = ProjectMember.leader(project, user, now); member.setId(2L);
        task = Task.create(project, "task", TaskSize.S, null, 0, now); task.setId(3L);
        when(access.project(1L)).thenReturn(project);
        when(access.lockedProject(1L)).thenReturn(project);
        when(access.approvedMember(1L, 1L)).thenReturn(member);
        when(tasks.findByIdAndProject_Id(3L, 1L)).thenReturn(Optional.of(task));
        when(issues.findById(3L)).thenReturn(Optional.empty());
        when(issues.saveAndFlush(any())).thenAnswer(c -> c.getArgument(0));
    }
    @Test void noIssueReturnsClosedRevisionZero() {
        var response = service.detail(1L, 3L, 1L);
        assertThat(response.open()).isFalse(); assertThat(response.revision()).isZero();
    }
    @Test void reportThenResolvePreservesContentAndIncrementsRevision() {
        var first = service.report(1L, 3L, 1L, "자료 부족", 0);
        assertThat(first.open()).isTrue(); assertThat(first.revision()).isEqualTo(1);
        var captor = org.mockito.ArgumentCaptor.forClass(TaskIssue.class);
        verify(issues).saveAndFlush(captor.capture());
        var issue = captor.getValue(); when(issues.findById(3L)).thenReturn(Optional.of(issue));
        var resolved = service.resolve(1L, 3L, 1L, 1);
        assertThat(resolved.open()).isFalse(); assertThat(resolved.content()).isEqualTo("자료 부족");
        assertThat(resolved.resolvedByMemberId()).isEqualTo(2L); assertThat(resolved.revision()).isEqualTo(2);
        assertThat(task.getProgress()).isZero(); assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }
    @Test void staleReportDoesNotOverwriteContent() {
        var issue = TaskIssue.create(task); issue.report("다른 팀원의 최신 내용", member, OffsetDateTime.now(clock));
        when(issues.findById(3L)).thenReturn(Optional.of(issue));
        assertThatThrownBy(() -> service.report(1L, 3L, 1L, "오래된 내용", 0))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("ISSUE_VERSION_CONFLICT"));
        assertThat(issue.getContent()).isEqualTo("다른 팀원의 최신 내용");
    }
    @Test void unassignedMemberCannotResolveButAssignedMemberCan() {
        member.setRole(MemberRole.MEMBER);
        assertThatThrownBy(() -> service.resolve(1L, 3L, 1L, 0))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.getCode()).isEqualTo("TASK_ASSIGNEE_REQUIRED"));
        when(assignments.findForTask(3L)).thenReturn(List.of(TaskAssignee.assign(task, member, OffsetDateTime.now(clock))));
        assertThat(service.resolve(1L, 3L, 1L, 0).revision()).isZero();
    }
    @Test void blankReportAndCancelledTaskAreRejected() {
        assertThatThrownBy(() -> service.report(1L, 3L, 1L, "  ", 0)).isInstanceOf(ApiException.class);
        task.cancel(OffsetDateTime.now(clock));
        assertThatThrownBy(() -> service.resolve(1L, 3L, 1L, 0)).isInstanceOf(ApiException.class);
    }
}
