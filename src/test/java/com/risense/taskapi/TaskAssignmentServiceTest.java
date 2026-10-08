package com.risense.taskapi;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.risense.api.error.ApiException;
import com.risense.domain.member.*;
import com.risense.domain.project.Project;
import com.risense.domain.task.*;
import com.risense.domain.user.User;
import java.time.OffsetDateTime;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskAssignmentServiceTest {
    private final TaskAssigneeRepository assignments = mock(TaskAssigneeRepository.class);
    private final TaskAssigneeHistoryRepository histories = mock(TaskAssigneeHistoryRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final SubTaskRepository children = mock(SubTaskRepository.class);
    private final TaskAssignmentService service = new TaskAssignmentService(assignments, histories, members, children);
    private final OffsetDateTime now = OffsetDateTime.parse("2026-10-08T00:00:00Z");
    private Task task;
    private ProjectMember actor, member, other;

    @BeforeEach void setup() {
        var user = User.register("test@example.com", "hash", "팀원", now); user.setId(1L);
        var project = Project.create(user, now); project.setId(10L);
        actor = ProjectMember.leader(project, user, now); actor.setId(1L);
        member = ProjectMember.leader(project, user, now); member.setId(2L); member.setRole(MemberRole.MEMBER);
        other = ProjectMember.leader(project, user, now); other.setId(3L); other.setRole(MemberRole.MEMBER);
        task = Task.create(project, "작업", TaskSize.M, null, 0, now); task.setId(20L);
        when(members.findByIdAndProject_Id(2L, 10L)).thenReturn(Optional.of(member));
        when(members.findByIdAndProject_Id(3L, 10L)).thenReturn(Optional.of(other));
    }

    @Test void newAssignmentRecordsActorAndTime() {
        assertThat(service.replace(task, List.of(2L), actor, now)).isTrue();
        verify(histories).saveAll(argThat(events -> {
            var iterator = events.iterator(); var event = iterator.next();
            return !iterator.hasNext() && event.getAction() == AssigneeAction.ASSIGN && event.getMember() == member
                    && event.getChangedBy() == actor && now.equals(event.getChangedAt());
        }));
        verify(assignments).saveAll(argThat(values -> {
            var a = values.iterator().next();
            return a.getId().equals(new TaskAssigneeId(20L, 2L)) && a.getAssignedAt().equals(now);
        }));
    }

    @Test void identicalSetProducesNoHistoryOrWrites() {
        var existing = TaskAssignee.assign(task, member, now.minusDays(1));
        when(assignments.findForTask(20L)).thenReturn(List.of(existing));
        assertThat(service.replace(task, List.of(2L), actor, now)).isFalse();
        assertThat(existing.getAssignedAt()).isEqualTo(now.minusDays(1));
        verifyNoInteractions(histories);
        verify(assignments, never()).saveAll(any()); verify(assignments, never()).delete(any());
    }

    @Test void replacementRecordsOnlyDifference() {
        var existing = TaskAssignee.assign(task, member, now.minusDays(1));
        when(assignments.findForTask(20L)).thenReturn(List.of(existing));
        assertThat(service.replace(task, List.of(3L), actor, now)).isTrue();
        verify(assignments).delete(existing);
        verify(histories).saveAll(argThat(values -> {
            var events = new ArrayList<TaskAssigneeHistory>(); values.forEach(events::add);
            return events.size() == 2 && events.get(0).getAction() == AssigneeAction.UNASSIGN
                    && events.get(0).getMember() == member && events.get(1).getAction() == AssigneeAction.ASSIGN
                    && events.get(1).getMember() == other;
        }));
    }

    @Test void emptyListUnassignsAll() {
        var existing = TaskAssignee.assign(task, member, now);
        when(assignments.findForTask(20L)).thenReturn(List.of(existing));
        assertThat(service.replace(task, List.of(), actor, now)).isTrue();
        verify(assignments).delete(existing);
        verify(assignments, never()).saveAll(any());
        verify(histories).saveAll(argThat(events -> events.iterator().next().getAction() == AssigneeAction.UNASSIGN));
    }

    @Test void invalidIdsOrNonapprovedMembersNeverWrite() {
        for (var ids : Arrays.asList(List.of(2L, 2L), List.of(-1L), Arrays.asList((Long) null))) {
            assertThatThrownBy(() -> service.replace(task, ids, actor, now)).isInstanceOf(ApiException.class);
        }
        member.setJoinStatus(MemberStatus.PENDING);
        assertThatThrownBy(() -> service.replace(task, List.of(2L), actor, now)).isInstanceOf(ApiException.class);
        member.setJoinStatus(MemberStatus.REMOVED);
        assertThatThrownBy(() -> service.replace(task, List.of(2L), actor, now)).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> service.replace(task, List.of(999L), actor, now)).isInstanceOf(ApiException.class);
        verify(assignments, never()).findForTask(anyLong()); verifyNoInteractions(histories);
    }

    @Test void removingMemberUnassignsWorkAndRecordsRemovingActor() {
        var child = SubTask.create(task, "하위 작업", member, 0);
        when(children.findForMember(2L)).thenReturn(List.of(child));
        var existing = TaskAssignee.assign(task, member, now.minusDays(1));
        when(assignments.findForMember(2L)).thenReturn(List.of(existing));
        service.removeMember(member, actor, now);
        verify(assignments).delete(existing);
        verify(histories).saveAll(argThat(events -> {
            var event = events.iterator().next();
            return event.getAction() == AssigneeAction.UNASSIGN && event.getChangedBy() == actor && event.getMember() == member;
        }));
        assertThat(task.getUpdatedAt()).isEqualTo(now);
        assertThat(child.getAssigneeMember()).isNull();
    }
}
