package com.risense.taskapi;

import com.risense.api.error.ApiException;
import com.risense.domain.member.ProjectMember;
import com.risense.domain.project.Project;
import com.risense.domain.task.*;
import com.risense.project.ProjectAccess;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
    private final ProjectAccess access;
    private final TaskRepository tasks;
    private final TaskAssigneeRepository assignees;
    private final TaskAssigneeHistoryRepository histories;
    private final TaskAssignmentService assignments;
    private final Clock clock;

    public TaskService(ProjectAccess access, TaskRepository tasks, TaskAssigneeRepository assignees,
            TaskAssigneeHistoryRepository histories, TaskAssignmentService assignments, Clock clock) {
        this.access = access;
        this.tasks = tasks;
        this.assignees = assignees;
        this.histories = histories;
        this.assignments = assignments;
        this.clock = clock;
    }

    @Transactional
    public TaskResponse create(long projectId, long userId, TaskRequest request) {
        Project project = access.lockedProject(projectId);
        access.manager(projectId, userId);
        access.requireWritable(project);
        int order;
        if (request.sortOrder() != null) order = request.sortOrder();
        else {
            int max = tasks.maxSortOrder(projectId);
            if (max == Integer.MAX_VALUE) throw new ApiException(HttpStatus.CONFLICT, "TASK_ORDER_LIMIT", "작업 정렬 순서 범위를 초과했습니다.");
            order = max + 1;
        }
        var task = tasks.saveAndFlush(Task.create(project, request.title(), request.size(), request.dueDate(), order, now()));
        return TaskResponse.from(task, List.of());
    }

    @Transactional(readOnly = true)
    public List<TaskResponse> list(long projectId, long userId) {
        readable(projectId, userId);
        var found = tasks.findByProject_IdOrderBySortOrderAscIdAsc(projectId);
        if (found.isEmpty()) return List.of();
        var byTask = assignees.findForTasks(found.stream().map(Task::getId).toList()).stream()
                .collect(Collectors.groupingBy(a -> a.getTask().getId()));
        return found.stream().map(task -> TaskResponse.from(task, byTask.getOrDefault(task.getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public TaskResponse detail(long projectId, long taskId, long userId) {
        readable(projectId, userId);
        return response(task(projectId, taskId));
    }

    @Transactional
    public TaskResponse update(long projectId, long taskId, long userId, TaskRequest request) {
        writable(projectId, userId);
        var task = task(projectId, taskId);
        editable(task);
        task.setTitle(request.title());
        task.setSize(request.size());
        task.setDueDate(request.dueDate());
        if (request.sortOrder() != null) task.setSortOrder(request.sortOrder());
        task.setUpdatedAt(now());
        return response(task);
    }

    @Transactional
    public TaskResponse cancel(long projectId, long taskId, long userId) {
        writable(projectId, userId);
        var task = task(projectId, taskId);
        if (task.getStatus() != TaskStatus.CANCELLED) task.cancel(now());
        return response(task);
    }

    @Transactional
    public TaskResponse setAssignees(long projectId, long taskId, long userId, AssigneeRequest request) {
        var actor = writable(projectId, userId);
        var task = task(projectId, taskId);
        editable(task);
        var now = now();
        if (assignments.replace(task, request.memberIds(), actor, now)) task.setUpdatedAt(now);
        return response(task);
    }

    @Transactional(readOnly = true)
    public List<AssigneeHistoryResponse> history(long projectId, long taskId, long userId) {
        readable(projectId, userId);
        task(projectId, taskId);
        return histories.findForTask(taskId).stream().map(AssigneeHistoryResponse::from).toList();
    }

    private void readable(long projectId, long userId) {
        access.project(projectId);
        access.approvedMember(projectId, userId);
    }

    private ProjectMember writable(long projectId, long userId) {
        var project = access.lockedProject(projectId);
        var actor = access.manager(projectId, userId);
        access.requireWritable(project);
        return actor;
    }

    private Task task(long projectId, long taskId) {
        return tasks.findByIdAndProject_Id(taskId, projectId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "프로젝트 작업을 찾을 수 없습니다."));
    }

    private void editable(Task task) {
        if (task.getStatus() == TaskStatus.CANCELLED) {
            throw new ApiException(HttpStatus.CONFLICT, "TASK_CANCELLED", "취소된 작업은 수정할 수 없습니다.");
        }
    }

    private TaskResponse response(Task task) { return TaskResponse.from(task, assignees.findForTask(task.getId())); }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
}
