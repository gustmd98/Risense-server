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

class TaskRelationServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskPrerequisiteRepository prerequisites = mock(TaskPrerequisiteRepository.class);
    private final SubTaskRepository children = mock(SubTaskRepository.class);
    private final TaskArtifactRepository artifacts = mock(TaskArtifactRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    private final TaskRelationService service = new TaskRelationService(new ProjectAccess(projects, members), tasks,
            members, prerequisites, children, artifacts, clock);
    private Project project;
    private ProjectMember actor, member;
    private Task task, other, third;
    private SubTask child;
    private TaskArtifact artifact;

    @BeforeEach void setup() {
        var user = User.register("test@example.com", "hash", "팀장", now()); user.setId(1L);
        project = Project.create(user, now()); project.setId(10L);
        actor = ProjectMember.leader(project, user, now()); actor.setId(1L);
        member = ProjectMember.leader(project, user, now()); member.setId(2L); member.setRole(MemberRole.MEMBER);
        task = newTask(20); other = newTask(21); third = newTask(22);
        child = SubTask.create(task, "하위 작업", member, 0); child.setId(30L);
        artifact = TaskArtifact.create(task, "산출물", "https://example.com/result", member, now().minusDays(1)); artifact.setId(40L);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(projects.findLockedById(10L)).thenReturn(Optional.of(project));
        when(members.findByProject_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(actor));
        when(members.findByIdAndProject_Id(2L, 10L)).thenReturn(Optional.of(member));
        for (var t : List.of(task, other, third)) when(tasks.findByIdAndProject_Id(t.getId(), 10L)).thenReturn(Optional.of(t));
        when(children.findByIdAndTask_Id(30L, 20L)).thenReturn(Optional.of(child));
        when(artifacts.findByIdAndTask_Id(40L, 20L)).thenReturn(Optional.of(artifact));
    }

    @Test void completingAndUndoingChildUpdatesParentProgress() {
        when(children.findForTask(20L)).thenReturn(List.of(child));
        service.completeChild(10L, 20L, 30L, 1L, new RelationRequests.Completion(true));
        assertThat(task.getProgress()).isEqualTo(100);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.DONE);
        service.completeChild(10L, 20L, 30L, 1L, new RelationRequests.Completion(false));
        assertThat(task.getProgress()).isZero();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getCompletedAt()).isNull();
    }

    @Test void deletingLastChildReopensParent() {
        task.setStatus(TaskStatus.DONE); task.setProgress(100); task.setCompletedAt(now());
        when(children.findForTask(20L)).thenReturn(List.of());
        service.deleteChild(10L, 20L, 30L, 1L);
        assertThat(task.getProgress()).isZero();
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
    }

    @Test void invalidAndCrossProjectPrerequisitesDoNotWrite() {
        for (var ids : List.of(List.of(20L), List.of(21L, 21L))) {
            error(() -> service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(ids)), HttpStatus.BAD_REQUEST, "INVALID_PREREQUISITES");
        }
        error(() -> service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(List.of(999L))), HttpStatus.NOT_FOUND, "TASK_NOT_FOUND");
        other.setStatus(TaskStatus.CANCELLED);
        error(() -> service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(List.of(21L))), HttpStatus.BAD_REQUEST, "INVALID_PREREQUISITES");
        verify(prerequisites, never()).saveAll(any()); verify(prerequisites, never()).delete(any());
    }

    @Test void cycleIsRejectedBeforeAnyGraphMutation() {
        when(prerequisites.findByTask_Project_Id(10L)).thenReturn(List.of(TaskPrerequisite.of(other, third), TaskPrerequisite.of(third, task)));
        error(() -> service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(List.of(21L))), HttpStatus.CONFLICT, "TASK_DEPENDENCY_CYCLE");
        verify(prerequisites, never()).saveAll(any()); verify(prerequisites, never()).delete(any());
        verify(projects).findLockedById(10L);
    }

    @Test void replacesDifferenceAndUnchangedRequestPreservesUpdatedAt() {
        var edge = TaskPrerequisite.of(task, other);
        when(prerequisites.findForTask(20L)).thenReturn(List.of(edge));
        when(prerequisites.findByTask_Project_Id(10L)).thenReturn(List.of(edge));
        var before = task.getUpdatedAt();
        service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(List.of(21L)));
        assertThat(task.getUpdatedAt()).isEqualTo(before);
        verify(prerequisites, never()).saveAll(any()); verify(prerequisites, never()).delete(any());
        assertThat(service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(List.of(22L))).getFirst().taskId()).isEqualTo(22L);
        verify(prerequisites).delete(edge);
        verify(prerequisites).saveAll(argThat(edges -> edges.iterator().next().getId().equals(new TaskPrerequisiteId(20L, 22L))));
    }

    @Test void emptyPrerequisiteListRemovesEdges() {
        var edge = TaskPrerequisite.of(task, other);
        when(prerequisites.findForTask(20L)).thenReturn(List.of(edge));
        assertThat(service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(List.of()))).isEmpty();
        verify(prerequisites).delete(edge);
    }

    @Test void childCreationAssignsApprovedMemberAndInitialFalse() {
        when(children.maxSortOrder(20L)).thenReturn(2);
        when(children.saveAndFlush(any())).thenAnswer(call -> { SubTask s = call.getArgument(0); s.setId(31L); return s; });
        var result = service.createChild(10L, 20L, 1L, settings(2L));
        assertThat(result.assigneeMemberId()).isEqualTo(2L);
        assertThat(result.completed()).isFalse(); assertThat(result.sortOrder()).isEqualTo(3);
    }

    @Test void rejectedChildAssigneeDoesNotMutateExistingChild() {
        member.setJoinStatus(MemberStatus.PENDING);
        error(() -> service.updateChild(10L, 20L, 30L, 1L, settings(2L)), HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEES");
        assertThat(child.getTitle()).isEqualTo("하위 작업");
        error(() -> service.createChild(10L, 20L, 1L, settings(999L)), HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEES");
    }

    @Test void onlyManagerCanChangeChildStructureButAssignedMemberCanComplete() {
        actor.setRole(MemberRole.MEMBER);
        error(() -> service.updateChild(10L, 20L, 30L, 1L, settings(null)), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        error(() -> service.completeChild(10L, 20L, 30L, 1L, new RelationRequests.Completion(true)), HttpStatus.FORBIDDEN, "SUBTASK_ASSIGNEE_REQUIRED");
        child.setAssigneeMember(actor);
        assertThat(service.completeChild(10L, 20L, 30L, 1L, new RelationRequests.Completion(true)).completed()).isTrue();
        assertThat(task.getProgress()).isZero(); assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test void childClearAssigneeKeepsCompletionAndOrderAndManagerCanDelete() {
        child.setCompleted(true);
        var result = service.updateChild(10L, 20L, 30L, 1L, settings(null));
        assertThat(result.assigneeMemberId()).isNull(); assertThat(result.completed()).isTrue(); assertThat(result.sortOrder()).isZero();
        service.deleteChild(10L, 20L, 30L, 1L);
        verify(children).delete(child);
    }

    @Test void missingChildAndArtifactAreScoped404() {
        error(() -> service.deleteChild(10L, 20L, 999L, 1L), HttpStatus.NOT_FOUND, "SUBTASK_NOT_FOUND");
        error(() -> service.deleteArtifact(10L, 20L, 999L, 1L), HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND");
    }

    @Test void approvedMemberCanCreateArtifactWithOwnIdentity() {
        actor.setRole(MemberRole.MEMBER);
        when(artifacts.saveAndFlush(any())).thenAnswer(call -> { TaskArtifact a = call.getArgument(0); a.setId(41L); return a; });
        var result = service.createArtifact(10L, 20L, 1L, link("https://example.com/result"));
        assertThat(result.createdByMemberId()).isEqualTo(1L); assertThat(result.createdAt()).isEqualTo(now());
    }

    @Test void artifactEditAndDeleteRequireOwnerOrManager() {
        actor.setRole(MemberRole.MEMBER);
        error(() -> service.updateArtifact(10L, 20L, 40L, 1L, link("https://example.com/new")), HttpStatus.FORBIDDEN, "ARTIFACT_OWNER_REQUIRED");
        error(() -> service.deleteArtifact(10L, 20L, 40L, 1L), HttpStatus.FORBIDDEN, "ARTIFACT_OWNER_REQUIRED");
        artifact.setCreatedBy(actor);
        var originalDate = artifact.getCreatedAt();
        assertThat(service.updateArtifact(10L, 20L, 40L, 1L, link("https://example.com/new")).createdAt()).isEqualTo(originalDate);
        actor.setRole(MemberRole.CO_LEADER); artifact.setCreatedBy(member);
        service.deleteArtifact(10L, 20L, 40L, 1L); verify(artifacts).delete(artifact);
    }

    @Test void nonWebAndCredentialUrlsAreRejected() {
        for (var url : List.of("javascript:alert(1)", "file:///etc/passwd", "ftp://example.com/a", "/relative", "https://user:password@example.com/a", "https://")) {
            error(() -> service.createArtifact(10L, 20L, 1L, link(url)), HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT_URL");
        }
        verify(artifacts, never()).saveAndFlush(any());
    }

    @Test void closedProjectBlocksAllRelationWrites() {
        project.setStatus(ProjectStatus.CLOSED);
        error(() -> service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(List.of())), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        error(() -> service.createChild(10L, 20L, 1L, settings(null)), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        error(() -> service.updateChild(10L, 20L, 30L, 1L, settings(null)), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        error(() -> service.completeChild(10L, 20L, 30L, 1L, new RelationRequests.Completion(true)), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        error(() -> service.deleteChild(10L, 20L, 30L, 1L), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        error(() -> service.createArtifact(10L, 20L, 1L, link("https://example.com")), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        error(() -> service.updateArtifact(10L, 20L, 40L, 1L, link("https://example.com")), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        error(() -> service.deleteArtifact(10L, 20L, 40L, 1L), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
        assertThat(service.children(10L, 20L, 1L)).isEmpty();
    }

    @Test void cancelledTaskRejectsRelationMutations() {
        task.setStatus(TaskStatus.CANCELLED);
        error(() -> service.createChild(10L, 20L, 1L, settings(null)), HttpStatus.CONFLICT, "TASK_CANCELLED");
        error(() -> service.createArtifact(10L, 20L, 1L, link("https://example.com")), HttpStatus.CONFLICT, "TASK_CANCELLED");
        error(() -> service.setPrerequisites(10L, 20L, 1L, new RelationRequests.Prerequisites(List.of())), HttpStatus.CONFLICT, "TASK_CANCELLED");
    }

    private Task newTask(long id) { var t = Task.create(project, "작업", TaskSize.M, null, 0, now().minusDays(1)); t.setId(id); return t; }
    private RelationRequests.SubTaskSettings settings(Long id) { return new RelationRequests.SubTaskSettings("수정 하위 작업", id, null); }
    private RelationRequests.Artifact link(String url) { return new RelationRequests.Artifact("산출물", url); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status); assertThat(e.getCode()).isEqualTo(code);
        });
    }
}
