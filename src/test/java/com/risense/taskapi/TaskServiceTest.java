package com.risense.taskapi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
import org.springframework.http.HttpStatus;

class TaskServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskAssigneeRepository assignees = mock(TaskAssigneeRepository.class);
    private final TaskAssigneeHistoryRepository histories = mock(TaskAssigneeHistoryRepository.class);
    private final TaskAssignmentService assignments = mock(TaskAssignmentService.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    private final TaskService service = new TaskService(new ProjectAccess(projects, members), tasks, assignees, histories, assignments, clock, mock(com.risense.checkin.CheckinLifecycle.class));
    private Project project;
    private ProjectMember actor;
    private Task task;

    @BeforeEach void setup() {
        var user = User.register("test@example.com", "hash", "팀장", now()); user.setId(1L);
        project = Project.create(user, now()); project.setId(10L);
        actor = ProjectMember.leader(project, user, now()); actor.setId(1L);
        task = Task.create(project, "작업", TaskSize.M, null, 0, now().minusHours(1)); task.setId(20L);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(projects.findLockedById(10L)).thenReturn(Optional.of(project));
        when(members.findByProject_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(actor));
        when(tasks.findByIdAndProject_Id(20L, 10L)).thenReturn(Optional.of(task));
    }

    @Test void createUsesNextOrderAndInitialState() {
        when(tasks.maxSortOrder(10L)).thenReturn(4);
        when(tasks.saveAndFlush(any())).thenAnswer(call -> { Task t = call.getArgument(0); t.setId(21L); return t; });
        var result = service.create(10L, 1L, request(null));
        assertThat(result.sortOrder()).isEqualTo(5);
        assertThat(result.status()).isEqualTo(TaskStatus.TODO);
        assertThat(result.progress()).isZero();
        assertThat(result.assignees()).isEmpty();
    }

    @Test void coleaderCanCreateWithExplicitOrder() {
        actor.setRole(MemberRole.CO_LEADER);
        when(tasks.saveAndFlush(any())).thenAnswer(call -> { Task t = call.getArgument(0); t.setId(21L); return t; });
        assertThat(service.create(10L, 1L, request(3)).sortOrder()).isEqualTo(3);
        verify(tasks, never()).maxSortOrder(anyLong());
    }

    @Test void ordinaryMemberReadsButCannotWrite() {
        actor.setRole(MemberRole.MEMBER);
        assertThat(service.detail(10L, 20L, 1L).id()).isEqualTo(20L);
        error(() -> service.create(10L, 1L, request(0)), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        error(() -> service.update(10L, 20L, 1L, request(0)), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        error(() -> service.cancel(10L, 20L, 1L), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        error(() -> service.setAssignees(10L, 20L, 1L, new AssigneeRequest(List.of())), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
    }

    @Test void outsiderAndOtherProjectTaskAreDenied() {
        error(() -> service.detail(10L, 999L, 1L), HttpStatus.NOT_FOUND, "TASK_NOT_FOUND");
        when(members.findByProject_IdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        error(() -> service.detail(10L, 20L, 1L), HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED");
        error(() -> service.history(10L, 20L, 1L), HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED");
    }

    @Test void updatePreservesCheckinStateAndClearsDeadline() {
        task.setStatus(TaskStatus.BLOCKED); task.setProgress(55); task.setDueDate(LocalDate.of(2026, 12, 10));
        var result = service.update(10L, 20L, 1L, request(null));
        assertThat(result.title()).isEqualTo("수정 작업");
        assertThat(result.size()).isEqualTo(TaskSize.XL);
        assertThat(result.dueDate()).isNull();
        assertThat(result.sortOrder()).isZero();
        assertThat(result.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(result.progress()).isEqualTo(55);
    }

    @Test void cancellationIsIdempotentAndBlocksEditsButKeepsRead() {
        assertThat(service.cancel(10L, 20L, 1L).status()).isEqualTo(TaskStatus.CANCELLED);
        task.setCancelledAt(now().minusDays(1));
        assertThat(service.cancel(10L, 20L, 1L).cancelledAt()).isEqualTo(now().minusDays(1));
        error(() -> service.update(10L, 20L, 1L, request(0)), HttpStatus.CONFLICT, "TASK_CANCELLED");
        error(() -> service.setAssignees(10L, 20L, 1L, new AssigneeRequest(List.of())), HttpStatus.CONFLICT, "TASK_CANCELLED");
        assertThat(service.detail(10L, 20L, 1L).status()).isEqualTo(TaskStatus.CANCELLED);
    }

    @Test void closedAndDoneProjectsBlockAllWrites() {
        for (var state : List.of(ProjectStatus.DONE, ProjectStatus.CLOSED)) {
            project.setStatus(state);
            error(() -> service.create(10L, 1L, request(0)), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.update(10L, 20L, 1L, request(0)), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.cancel(10L, 20L, 1L), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.setAssignees(10L, 20L, 1L, new AssigneeRequest(List.of())), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            assertThat(service.detail(10L, 20L, 1L).id()).isEqualTo(20L);
        }
        verifyNoInteractions(assignments);
    }

    @Test void unchangedAssigneesDoNotTouchUpdatedAt() {
        var before = task.getUpdatedAt();
        var request = new AssigneeRequest(List.of(1L));
        service.setAssignees(10L, 20L, 1L, request);
        assertThat(task.getUpdatedAt()).isEqualTo(before);
        when(assignments.replace(task, request.memberIds(), actor, now())).thenReturn(true);
        service.setAssignees(10L, 20L, 1L, request);
        assertThat(task.getUpdatedAt()).isEqualTo(now());
    }

    @Test void listLoadsAssigneesInOneBatchAndIncludesCancelled() {
        task.setStatus(TaskStatus.CANCELLED);
        when(tasks.findByProject_IdOrderBySortOrderAscIdAsc(10L)).thenReturn(List.of(task));
        when(assignees.findForTasks(List.of(20L))).thenReturn(List.of(TaskAssignee.assign(task, actor, now())));
        var result = service.list(10L, 1L);
        assertThat(result.getFirst().assignees().getFirst().memberId()).isEqualTo(1L);
        assertThat(result.getFirst().status()).isEqualTo(TaskStatus.CANCELLED);
        verify(assignees, never()).findForTask(anyLong());
    }

    @Test void orderOverflowDoesNotSave() {
        when(tasks.maxSortOrder(10L)).thenReturn(Integer.MAX_VALUE);
        error(() -> service.create(10L, 1L, request(null)), HttpStatus.CONFLICT, "TASK_ORDER_LIMIT");
        verify(tasks, never()).saveAndFlush(any());
    }

    private TaskRequest request(Integer order) { return new TaskRequest(" 수정 작업 ", TaskSize.XL, null, order); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status); assertThat(e.getCode()).isEqualTo(code);
        });
    }
}
