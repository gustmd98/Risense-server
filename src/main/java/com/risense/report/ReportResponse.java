package com.risense.report;

import com.risense.checkin.CheckinData.Summary;
import com.risense.domain.member.MemberRole;
import com.risense.domain.member.MemberStatus;
import com.risense.domain.project.ProjectStatus;
import java.time.*;
import java.util.List;

public record ReportResponse(ProjectInfo project, OffsetDateTime generatedAt, OffsetDateTime asOf,
        TaskSummary tasks, Summary checkins, List<Member> members, List<Event> events,
        boolean eventsTruncated, OffsetDateTime issueEventHistoryCompleteSince) {
    public record ProjectInfo(long id, String title, String className, String projectType,
            LocalDate startDate, LocalDate deadline, ProjectStatus status, OffsetDateTime closedAt) {}
    public record TaskSummary(long totalTasks, long completedTasks, long overdueTasks,
            long cancelledTasks, long completedLateTasks) {}
    public record Member(long memberId, long userId, String nickname, MemberRole role, MemberStatus joinStatus,
            Summary checkins, long completedTasks, long overdueTasks) {}
    public record Event(String id, String type, OffsetDateTime occurredAt, Long taskId, String taskTitle,
            Long memberId, String nickname, Long actorMemberId, String actorNickname,
            List<Long> relatedTaskIds, String content, boolean backfilled) {}
}
