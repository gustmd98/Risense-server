package com.risense.taskapi;

import com.risense.domain.task.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record TaskResponse(Long id, Long projectId, String title, TaskSize size, TaskStatus status,
        Integer progress, LocalDate dueDate, Integer sortOrder, List<Assignee> assignees,
        OffsetDateTime cancelledAt, OffsetDateTime completedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt,
        LocalDate startDate, long revision, Double riskScore, boolean overdue, boolean dueSoon,
        boolean missingCompletionEvidence) {
    public record Assignee(Long memberId, String nickname, OffsetDateTime assignedAt) {}
    static TaskResponse from(Task task, List<TaskAssignee> assignments) {
        return from(task, assignments, null, false);
    }
    static TaskResponse from(Task task, List<TaskAssignee> assignments, LocalDate today, boolean hasArtifacts) {
        boolean active = task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED;
        boolean overdue = active && today != null && task.getDueDate() != null && task.getDueDate().isBefore(today);
        boolean dueSoon = active && today != null && task.getDueDate() != null
                && !task.getDueDate().isBefore(today) && !task.getDueDate().isAfter(today.plusDays(3));
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getTitle(), task.getSize(),
                task.getStatus(), task.getProgress(), task.getDueDate(), task.getSortOrder(), assignments.stream()
                .map(a -> new Assignee(a.getMember().getId(), a.getMember().getUser().getNickname(), a.getAssignedAt())).toList(),
                task.getCancelledAt(), task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt(),
                task.getStartDate(), task.getRevision(), null, overdue, dueSoon,
                task.getStatus() == TaskStatus.DONE && !hasArtifacts);
    }
}
