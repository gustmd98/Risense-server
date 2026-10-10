package com.risense.taskapi;

import com.risense.checkin.CheckinLifecycle;

import com.risense.api.error.ApiException;
import com.risense.domain.member.*;
import com.risense.domain.task.*;
import com.risense.project.ProjectAccess;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskRelationService {
    private final CheckinLifecycle checkins;
    private final ProjectAccess access;
    private final TaskRepository tasks;
    private final ProjectMemberRepository members;
    private final TaskPrerequisiteRepository prerequisites;
    private final SubTaskRepository children;
    private final TaskArtifactRepository artifacts;
    private final Clock clock;

    public TaskRelationService(ProjectAccess access, TaskRepository tasks, ProjectMemberRepository members,
            TaskPrerequisiteRepository prerequisites, SubTaskRepository children, TaskArtifactRepository artifacts, Clock clock, CheckinLifecycle checkins) {
        this.checkins = checkins;
        this.access = access;
        this.tasks = tasks;
        this.members = members;
        this.prerequisites = prerequisites;
        this.children = children;
        this.artifacts = artifacts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<RelationResponses.Prerequisite> prerequisites(long projectId, long taskId, long userId) {
        readable(projectId, taskId, userId);
        return prerequisites.findForTask(taskId).stream().map(RelationResponses.Prerequisite::from).toList();
    }

    @Transactional
    public List<RelationResponses.Prerequisite> setPrerequisites(long projectId, long taskId, long userId,
            RelationRequests.Prerequisites request) {
        var write = writable(projectId, taskId, userId, true);
        var ids = request.prerequisiteTaskIds();
        if (ids == null || ids.stream().anyMatch(id -> id == null || id <= 0 || id == taskId)
                || new HashSet<>(ids).size() != ids.size()) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_PREREQUISITES", "선행 작업은 중복·자기 참조 없이 지정해야 합니다.");
        }
        Map<Long, Task> selected = new TreeMap<>();
        for (long id : ids) {
            var target = task(projectId, id);
            if (target.getStatus() == TaskStatus.CANCELLED) {
                throw error(HttpStatus.BAD_REQUEST, "INVALID_PREREQUISITES", "취소된 작업은 선행 작업으로 지정할 수 없습니다.");
            }
            selected.put(id, target);
        }
        if (TaskGraph.hasCycle(prerequisites.findByTask_Project_Id(projectId), taskId, ids)) {
            throw error(HttpStatus.CONFLICT, "TASK_DEPENDENCY_CYCLE", "선행 작업 관계에 순환이 생깁니다.");
        }
        var existing = prerequisites.findForTask(taskId);
        Set<Long> previous = new HashSet<>();
        boolean changed = false;
        for (var edge : existing) {
            long id = edge.getId().getPrerequisiteTaskId(); previous.add(id);
            if (!selected.containsKey(id)) { prerequisites.delete(edge); changed = true; }
        }
        var additions = selected.entrySet().stream().filter(entry -> !previous.contains(entry.getKey()))
                .map(entry -> TaskPrerequisite.of(write.task(), entry.getValue())).toList();
        if (!additions.isEmpty()) { prerequisites.saveAll(additions); changed = true; }
        if (changed) write.task().setUpdatedAt(now());
        return selected.values().stream().map(t -> new RelationResponses.Prerequisite(t.getId(), t.getTitle(), t.getStatus())).toList();
    }

    @Transactional(readOnly = true)
    public List<RelationResponses.Child> children(long projectId, long taskId, long userId) {
        readable(projectId, taskId, userId);
        return children.findForTask(taskId).stream().map(RelationResponses.Child::from).toList();
    }

    @Transactional
    public RelationResponses.Child createChild(long projectId, long taskId, long userId, RelationRequests.SubTaskSettings request) {
        var write = writable(projectId, taskId, userId, true);
        var assignee = assignee(projectId, request.assigneeMemberId());
        int order;
        if (request.sortOrder() != null) order = request.sortOrder();
        else {
            int max = children.maxSortOrder(taskId);
            if (max == Integer.MAX_VALUE) throw error(HttpStatus.CONFLICT, "SUBTASK_ORDER_LIMIT", "하위 작업 정렬 순서 범위를 초과했습니다.");
            order = max + 1;
        }
        checkins.beforeChange(projectId);
        var child = children.saveAndFlush(SubTask.create(write.task(), request.title(), assignee, order));
        recalculateProgress(write.task());
        write.task().setUpdatedAt(now());
        return RelationResponses.Child.from(child);
    }

    @Transactional
    public RelationResponses.Child updateChild(long projectId, long taskId, long childId, long userId,
            RelationRequests.SubTaskSettings request) {
        var write = writable(projectId, taskId, userId, true);
        var child = child(taskId, childId);
        var assignee = assignee(projectId, request.assigneeMemberId());
        child.setTitle(request.title()); child.setAssigneeMember(assignee);
        if (request.sortOrder() != null) child.setSortOrder(request.sortOrder());
        write.task().setUpdatedAt(now());
        return RelationResponses.Child.from(child);
    }

    @Transactional
    public RelationResponses.Child completeChild(long projectId, long taskId, long childId, long userId,
            RelationRequests.Completion request) {
        var write = writable(projectId, taskId, userId, false);
        var child = child(taskId, childId);
        if (!manager(write.actor()) && (child.getAssigneeMember() == null
                || !Objects.equals(child.getAssigneeMember().getId(), write.actor().getId()))) {
            throw error(HttpStatus.FORBIDDEN, "SUBTASK_ASSIGNEE_REQUIRED", "관리자 또는 해당 하위 작업 담당자만 완료 상태를 변경할 수 있습니다.");
        }
        if (request.completed() == null) throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "완료 여부를 입력해주세요.");
        if (!Objects.equals(child.getCompleted(), request.completed())) {
            checkins.beforeChange(projectId);
            child.setCompleted(request.completed());
            recalculateProgress(write.task());
            write.task().setUpdatedAt(now());
        }
        return RelationResponses.Child.from(child);
    }

    @Transactional
    public void deleteChild(long projectId, long taskId, long childId, long userId) {
        var write = writable(projectId, taskId, userId, true);
        var child = child(taskId, childId);
        checkins.beforeChange(projectId);
        children.delete(child);
        children.flush();
        recalculateProgress(write.task());
        write.task().setUpdatedAt(now());
    }

    @Transactional(readOnly = true)
    public List<RelationResponses.Artifact> artifacts(long projectId, long taskId, long userId) {
        readable(projectId, taskId, userId);
        return artifacts.findForTask(taskId).stream().map(RelationResponses.Artifact::from).toList();
    }

    @Transactional
    public RelationResponses.Artifact createArtifact(long projectId, long taskId, long userId, RelationRequests.Artifact request) {
        var write = writable(projectId, taskId, userId, false);
        validateUrl(request.url());
        var artifact = artifacts.saveAndFlush(TaskArtifact.create(write.task(), request.title(), request.url(), write.actor(), now()));
        write.task().setUpdatedAt(now());
        return RelationResponses.Artifact.from(artifact);
    }

    @Transactional
    public RelationResponses.Artifact updateArtifact(long projectId, long taskId, long artifactId, long userId,
            RelationRequests.Artifact request) {
        var write = writable(projectId, taskId, userId, false);
        var artifact = artifact(taskId, artifactId);
        requireArtifactOwner(write.actor(), artifact);
        validateUrl(request.url());
        artifact.setTitle(request.title()); artifact.setUrl(request.url());
        write.task().setUpdatedAt(now());
        return RelationResponses.Artifact.from(artifact);
    }

    @Transactional
    public void deleteArtifact(long projectId, long taskId, long artifactId, long userId) {
        var write = writable(projectId, taskId, userId, false);
        var artifact = artifact(taskId, artifactId);
        requireArtifactOwner(write.actor(), artifact);
        artifacts.delete(artifact); write.task().setUpdatedAt(now());
    }

    private void recalculateProgress(Task task) {
        var subtasks = children.findForTask(task.getId());
        long completed = subtasks.stream().filter(s -> Boolean.TRUE.equals(s.getCompleted())).count();
        TaskProgress.apply(task, subtasks.size(), completed, now());
    }

    private Task readable(long projectId, long taskId, long userId) {
        access.project(projectId); access.approvedMember(projectId, userId);
        return task(projectId, taskId);
    }

    private record Write(Task task, ProjectMember actor) {}
    private Write writable(long projectId, long taskId, long userId, boolean managersOnly) {
        var project = access.lockedProject(projectId);
        var actor = managersOnly ? access.manager(projectId, userId) : access.approvedMember(projectId, userId);
        access.requireWritable(project);
        var task = task(projectId, taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) throw error(HttpStatus.CONFLICT, "TASK_CANCELLED", "취소된 작업은 수정할 수 없습니다.");
        return new Write(task, actor);
    }

    private ProjectMember assignee(long projectId, Long memberId) {
        if (memberId == null) return null;
        return members.findByIdAndProject_Id(memberId, projectId).filter(m -> m.getJoinStatus() == MemberStatus.APPROVED)
                .orElseThrow(() -> error(HttpStatus.BAD_REQUEST, "INVALID_ASSIGNEES", "같은 프로젝트의 승인된 팀원만 담당자로 지정할 수 있습니다."));
    }
    private Task task(long projectId, long id) {
        return tasks.findByIdAndProject_Id(id, projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "프로젝트 작업을 찾을 수 없습니다."));
    }
    private SubTask child(long taskId, long childId) {
        return children.findByIdAndTask_Id(childId, taskId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "SUBTASK_NOT_FOUND", "하위 작업을 찾을 수 없습니다."));
    }
    private TaskArtifact artifact(long taskId, long id) {
        return artifacts.findByIdAndTask_Id(id, taskId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "ARTIFACT_NOT_FOUND", "산출물을 찾을 수 없습니다."));
    }
    private void requireArtifactOwner(ProjectMember actor, TaskArtifact artifact) {
        if (!manager(actor) && !Objects.equals(actor.getId(), artifact.getCreatedBy().getId())) {
            throw error(HttpStatus.FORBIDDEN, "ARTIFACT_OWNER_REQUIRED", "작성자 또는 관리자만 산출물을 수정·삭제할 수 있습니다.");
        }
    }
    private boolean manager(ProjectMember member) { return member.getRole() == MemberRole.LEADER || member.getRole() == MemberRole.CO_LEADER; }
    private void validateUrl(String value) {
        try {
            URI uri = URI.create(value);
            if (("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getRawUserInfo() == null) return;
        } catch (IllegalArgumentException | NullPointerException ignored) { /* invalid URI */ }
        throw error(HttpStatus.BAD_REQUEST, "INVALID_ARTIFACT_URL", "사용자 인증정보 없이 HTTP 또는 HTTPS 링크를 입력해주세요.");
    }
    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private static ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
