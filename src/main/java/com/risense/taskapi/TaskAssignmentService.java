package com.risense.taskapi;

import com.risense.checkin.CheckinLifecycle;

import com.risense.api.error.ApiException;
import com.risense.domain.member.*;
import com.risense.domain.task.*;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Caller must hold the project's write lock and perform its project/role checks. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class TaskAssignmentService {
    private final CheckinLifecycle checkins;
    private final TaskAssigneeRepository assignments;
    private final TaskAssigneeHistoryRepository histories;
    private final ProjectMemberRepository members;
    private final SubTaskRepository children;

    public TaskAssignmentService(TaskAssigneeRepository assignments, TaskAssigneeHistoryRepository histories,
            ProjectMemberRepository members, SubTaskRepository children, CheckinLifecycle checkins) {
        this.checkins = checkins;
        this.assignments = assignments;
        this.histories = histories;
        this.members = members;
        this.children = children;
    }

    public boolean replace(Task task, List<Long> memberIds, ProjectMember actor, OffsetDateTime now) {
        if (memberIds == null || memberIds.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(memberIds).size() != memberIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEES", "담당자 ID는 양수이며 중복될 수 없습니다.");
        }
        Map<Long, ProjectMember> selected = new LinkedHashMap<>();
        // Validate every target before modifying any assignment or history.
        for (Long id : memberIds) {
            var member = members.findByIdAndProject_Id(id, task.getProject().getId())
                    .filter(m -> m.getJoinStatus() == MemberStatus.APPROVED)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEES", "같은 프로젝트의 승인된 팀원만 담당자로 지정할 수 있습니다."));
            selected.put(id, member);
        }
        checkins.beforeChange(task.getProject().getId());
        var existing = assignments.findForTask(task.getId());
        Set<Long> current = new HashSet<>();
        List<TaskAssigneeHistory> events = new ArrayList<>();
        for (var assignment : existing) {
            Long memberId = assignment.getMember().getId();
            current.add(memberId);
            if (!selected.containsKey(memberId)) {
                checkins.unassigned(task.getProject().getId(), task.getId(), memberId);
                assignments.delete(assignment);
                events.add(TaskAssigneeHistory.record(task, assignment.getMember(), AssigneeAction.UNASSIGN, actor, now));
            }
        }
        List<TaskAssignee> additions = new ArrayList<>();
        selected.forEach((id, member) -> {
            if (!current.contains(id)) {
                additions.add(TaskAssignee.assign(task, member, now));
                events.add(TaskAssigneeHistory.record(task, member, AssigneeAction.ASSIGN, actor, now));
            }
        });
        if (!additions.isEmpty()) assignments.saveAll(additions);
        if (!events.isEmpty()) histories.saveAll(events);
        return !events.isEmpty();
    }

    public void removeMember(ProjectMember member, ProjectMember actor, OffsetDateTime now) {
        for (var child : children.findForMember(member.getId())) {
            child.setAssigneeMember(null);
            child.getTask().setUpdatedAt(now);
        }
        List<TaskAssigneeHistory> events = new ArrayList<>();
        for (var assignment : assignments.findForMember(member.getId())) {
            Task task = assignment.getTask();
            assignments.delete(assignment);
            events.add(TaskAssigneeHistory.record(task, member, AssigneeAction.UNASSIGN, actor, now));
            task.setUpdatedAt(now);
        }
        if (!events.isEmpty()) histories.saveAll(events);
    }
}
