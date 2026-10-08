package com.risense.taskapi;

import com.risense.domain.task.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record TaskResponse(Long id, Long projectId, String title, TaskSize size, TaskStatus status,
        Integer progress, LocalDate dueDate, Integer sortOrder, List<Assignee> assignees,
        OffsetDateTime cancelledAt, OffsetDateTime completedAt, OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    public record Assignee(Long memberId, String nickname, OffsetDateTime assignedAt) {}
    static TaskResponse from(Task task, List<TaskAssignee> assignments) {
        return new TaskResponse(task.getId(), task.getProject().getId(), task.getTitle(), task.getSize(),
                task.getStatus(), task.getProgress(), task.getDueDate(), task.getSortOrder(), assignments.stream()
                .map(a -> new Assignee(a.getMember().getId(), a.getMember().getUser().getNickname(), a.getAssignedAt())).toList(),
                task.getCancelledAt(), task.getCompletedAt(), task.getCreatedAt(), task.getUpdatedAt());
    }
}
