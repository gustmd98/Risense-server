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

class TaskTemplateServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    private final TaskTemplateService service = new TaskTemplateService(new ProjectAccess(projects, members), tasks, clock);
    private Project project;
    private ProjectMember actor;

    @BeforeEach void setup() {
        var user = User.register("leader@example.com", "hash", "팀장", OffsetDateTime.now(clock)); user.setId(1L);
        project = Project.create(user, OffsetDateTime.now(clock)); project.setId(10L);
        actor = ProjectMember.leader(project, user, OffsetDateTime.now(clock)); actor.setId(2L);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(projects.findLockedById(10L)).thenReturn(Optional.of(project));
        when(members.findByProject_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(actor));
        when(tasks.saveAllAndFlush(anyList())).thenAnswer(call -> {
            List<Task> batch = call.getArgument(0);
            for (int i = 0; i < batch.size(); i++) batch.get(i).setId(100L + i);
            return batch;
        });
    }

    @Test void everyTemplateAppendsOrderedTasksWithInitialStateAndCommonDeadline() {
        var due = LocalDate.of(2026, 12, 15);
        when(tasks.maxSortOrder(10L)).thenReturn(7);
        for (var template : TaskTemplate.values()) {
            var result = service.create(10L, 1L, new TaskTemplateRequest(template, due));
            assertThat(result).hasSize(template.items().size());
            for (int i = 0; i < result.size(); i++) {
                var task = result.get(i);
                assertThat(task.projectId()).isEqualTo(10L);
                assertThat(task.title()).isEqualTo(template.items().get(i).title());
                assertThat(task.size()).isEqualTo(template.items().get(i).size());
                assertThat(task.sortOrder()).isEqualTo(8 + i);
                assertThat(task.dueDate()).isEqualTo(due);
                assertThat(task.status()).isEqualTo(TaskStatus.TODO);
                assertThat(task.progress()).isZero();
                assertThat(task.assignees()).isEmpty();
                assertThat(task.createdAt()).isEqualTo(OffsetDateTime.now(clock));
                assertThat(task.cancelledAt()).isNull();
                assertThat(task.completedAt()).isNull();
            }
        }
    }

    @Test void coleaderCanCreateInEmptyProjectWithoutDeadline() {
        actor.setRole(MemberRole.CO_LEADER);
        when(tasks.maxSortOrder(10L)).thenReturn(-1);
        var result = service.create(10L, 1L, request());
        assertThat(result.getFirst().sortOrder()).isZero();
        assertThat(result).allSatisfy(task -> assertThat(task.dueDate()).isNull());
    }

    @Test void memberCanPreviewButCannotCreateAndPendingMemberCannotPreview() {
        actor.setRole(MemberRole.MEMBER);
        assertThat(service.list(10L, 1L)).hasSize(5);
        error(() -> service.create(10L, 1L, request()), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        actor.setJoinStatus(MemberStatus.PENDING);
        error(() -> service.list(10L, 1L), HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED");
        verify(tasks, never()).saveAllAndFlush(any());
    }

    @Test void closedAndDoneProjectsAllowPreviewButBlockCreation() {
        for (var status : List.of(ProjectStatus.DONE, ProjectStatus.CLOSED)) {
            project.setStatus(status);
            assertThat(service.list(10L, 1L)).hasSize(5);
            error(() -> service.create(10L, 1L, request()), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        }
        verify(tasks, never()).saveAllAndFlush(any());
    }

    @Test void checksWholeBatchOrderCapacityBeforeSaving() {
        int count = TaskTemplate.PRESENTATION.items().size();
        when(tasks.maxSortOrder(10L)).thenReturn(Integer.MAX_VALUE - count + 1);
        error(() -> service.create(10L, 1L, request()), HttpStatus.CONFLICT, "TASK_ORDER_LIMIT");
        verify(tasks, never()).saveAllAndFlush(any());
        when(tasks.maxSortOrder(10L)).thenReturn(Integer.MAX_VALUE - count);
        assertThat(service.create(10L, 1L, request()).getLast().sortOrder()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test void missingProjectAndOutsiderCannotCreate() {
        error(() -> service.create(999L, 1L, request()), HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND");
        when(members.findByProject_IdAndUser_Id(10L, 1L)).thenReturn(Optional.empty());
        error(() -> service.create(10L, 1L, request()), HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED");
        verify(tasks, never()).saveAllAndFlush(any());
    }

    private TaskTemplateRequest request() { return new TaskTemplateRequest(TaskTemplate.PRESENTATION, null); }
    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status); assertThat(e.getCode()).isEqualTo(code);
        });
    }
}
