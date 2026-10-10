package com.risense.issue;
import java.time.OffsetDateTime;
public record IssueResponse(long taskId, boolean open, String content, long revision,
        Long reportedByMemberId, OffsetDateTime reportedAt, Long resolvedByMemberId, OffsetDateTime resolvedAt) {
    static IssueResponse empty(long taskId) { return new IssueResponse(taskId, false, null, 0, null, null, null, null); }
    static IssueResponse from(TaskIssue issue) {
        return new IssueResponse(issue.getTaskId(), issue.isOpen(), issue.getContent(), issue.getRevision(),
            issue.getReportedBy() == null ? null : issue.getReportedBy().getId(), issue.getReportedAt(),
            issue.getResolvedBy() == null ? null : issue.getResolvedBy().getId(), issue.getResolvedAt());
    }
}
