package com.risense.taskapi;

import com.risense.domain.task.*;
import java.time.OffsetDateTime;

public final class RelationResponses {
    private RelationResponses() {}
    public record Prerequisite(Long taskId, String title, TaskStatus status) {
        static Prerequisite from(TaskPrerequisite p) {
            var task = p.getPrerequisiteTask();
            return new Prerequisite(task.getId(), task.getTitle(), task.getStatus());
        }
    }
    public record Child(Long id, Long taskId, String title, Boolean completed,
            Long assigneeMemberId, String assigneeNickname, Integer sortOrder) {
        static Child from(SubTask sub) {
            var assignee = sub.getAssigneeMember();
            return new Child(sub.getId(), sub.getTask().getId(), sub.getTitle(), sub.getCompleted(),
                    assignee == null ? null : assignee.getId(), assignee == null ? null : assignee.getUser().getNickname(), sub.getSortOrder());
        }
    }
    public record Artifact(Long id, Long taskId, String title, String url, Long createdByMemberId,
            String createdByNickname, OffsetDateTime createdAt) {
        static Artifact from(TaskArtifact artifact) {
            return new Artifact(artifact.getId(), artifact.getTask().getId(), artifact.getTitle(), artifact.getUrl(),
                    artifact.getCreatedBy().getId(), artifact.getCreatedBy().getUser().getNickname(), artifact.getCreatedAt());
        }
    }
}
