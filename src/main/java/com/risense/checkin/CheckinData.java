package com.risense.checkin;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public final class CheckinData {
    private CheckinData() {}
    public record Round(long id, long projectId, LocalDate scheduledDate, Instant opensAt,
            Instant deadlineAt, Instant lateUntilAt) {
        public CheckinWindow window() { return new CheckinWindow(opensAt, deadlineAt, lateUntilAt); }
    }
    public record Child(long id, String title, boolean completed, Long assigneeMemberId) {}
    public record Artifact(long id, String title, String url, long createdByMemberId) {}
    public record Issue(boolean open, String content, long revision, Long reportedByMemberId,
            Instant reportedAt, Long resolvedByMemberId, Instant resolvedAt) {}
    public record TaskSnapshot(long taskId, String title, String size, String status, int progress,
            LocalDate dueDate, Instant taskUpdatedAt, List<Long> assigneeMemberIds,
            int totalChildren, long completedChildren, List<Child> children, List<Artifact> artifacts,
            Issue issue, String requestToTeam, String nextAction) {}
    public record Target(long taskId, String titleAtOpen, String excludedReason,
            String version, TaskSnapshot current) {}
    public record Submission(long roundId, long revision, boolean late, Instant submittedAt,
            Instant updatedAt, List<TaskSnapshot> tasks) {}
    public record Current(Round round, Instant serverTime, CheckinWindow.Phase phase,
            boolean canSubmit, boolean canEdit, String unavailableReason,
            List<Target> targets, Submission submission) {}
    public record Entry(@Positive long taskId, @NotBlank @Size(min=64,max=64) String expectedVersion,
            @Size(max=2000) String issueContent, @PositiveOrZero long expectedIssueRevision,
            @Size(max=2000) String requestToTeam, @Size(max=2000) String nextAction) {}
    public record Submit(@NotNull UUID requestKey, @NotNull @PositiveOrZero Long expectedRevision,
            @NotNull @Size(min=1) List<@NotNull @Valid Entry> tasks) {}
    public record MemberStatus(long memberId, String nickname, String status,
            boolean departed, int targetCount, Submission submission) {}
    public record Summary(long eligible, long submitted, long onTime, long late, long missed,
            long pending, java.math.BigDecimal submissionRate) {}
    public record Status(Round round, Instant serverTime, Summary summary, List<MemberStatus> members) {}
    public record HistoryPage(List<History> items, Long nextBeforeRoundId) {}
    public record History(Round round, String status, int targetCount, Submission submission) {}
}
