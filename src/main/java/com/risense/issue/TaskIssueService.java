package com.risense.issue;

import com.risense.api.error.ApiException;
import com.risense.domain.member.*;
import com.risense.domain.task.*;
import com.risense.project.ProjectAccess;
import java.time.*;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskIssueService {
    private final ProjectAccess access;
    private final TaskRepository tasks;
    private final TaskAssigneeRepository assignments;
    private final TaskIssueRepository issues;
    private final Clock clock;

    public TaskIssueService(ProjectAccess access, TaskRepository tasks, TaskAssigneeRepository assignments,
            TaskIssueRepository issues, Clock clock) {
        this.access = access; this.tasks = tasks; this.assignments = assignments; this.issues = issues; this.clock = clock;
    }
    @Transactional(readOnly = true)
    public IssueResponse detail(long projectId, long taskId, long userId) {
        access.project(projectId); access.approvedMember(projectId, userId); task(projectId, taskId);
        return issues.findById(taskId).map(IssueResponse::from).orElseGet(() -> IssueResponse.empty(taskId));
    }
    /** Called by the check-in service in the same transaction, not a separate immediate-report endpoint. */
    @Transactional
    public IssueResponse report(long projectId, long taskId, long userId, String content, long expectedRevision) {
        var actor = writable(projectId, taskId, userId);
        if (content == null || content.isBlank() || content.length() > 2000) {
            throw error(HttpStatus.BAD_REQUEST, "INVALID_ISSUE_CONTENT", "이슈 내용을 1~2000자로 입력해주세요.");
        }
        var issue = issues.findById(taskId).orElseGet(() -> TaskIssue.create(task(projectId, taskId)));
        version(issue.getRevision(), expectedRevision);
        if (!issue.isOpen() || !Objects.equals(issue.getContent(), content.trim())) {
            issue.report(content.trim(), actor, OffsetDateTime.now(clock));
            issues.saveAndFlush(issue);
        }
        return IssueResponse.from(issue);
    }
    @Transactional
    public IssueResponse resolve(long projectId, long taskId, long userId, long expectedRevision) {
        var actor = writable(projectId, taskId, userId);
        var issue = issues.findById(taskId).orElse(null);
        version(issue == null ? 0 : issue.getRevision(), expectedRevision);
        if (issue == null) return IssueResponse.empty(taskId);
        if (issue.isOpen()) { issue.resolve(actor, OffsetDateTime.now(clock)); issues.saveAndFlush(issue); }
        return IssueResponse.from(issue);
    }
    private ProjectMember writable(long projectId, long taskId, long userId) {
        var project = access.lockedProject(projectId);
        var actor = access.approvedMember(projectId, userId); access.requireWritable(project);
        var task = task(projectId, taskId);
        if (task.getStatus() == TaskStatus.CANCELLED) throw error(HttpStatus.CONFLICT, "TASK_CANCELLED", "취소된 작업은 수정할 수 없습니다.");
        boolean manager = actor.getRole() == MemberRole.LEADER || actor.getRole() == MemberRole.CO_LEADER;
        if (!manager && assignments.findForTask(taskId).stream().noneMatch(a -> Objects.equals(a.getMember().getId(), actor.getId()))) {
            throw error(HttpStatus.FORBIDDEN, "TASK_ASSIGNEE_REQUIRED", "작업 담당자 또는 관리자만 이슈를 변경할 수 있습니다.");
        }
        return actor;
    }
    private Task task(long projectId, long taskId) {
        return tasks.findByIdAndProject_Id(taskId, projectId).orElseThrow(() -> error(HttpStatus.NOT_FOUND, "TASK_NOT_FOUND", "프로젝트 작업을 찾을 수 없습니다."));
    }
    private void version(long actual, long expected) {
        if (expected < 0 || actual != expected) throw error(HttpStatus.CONFLICT, "ISSUE_VERSION_CONFLICT", "이슈가 변경되었습니다. 다시 조회해주세요.");
    }
    private static ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
