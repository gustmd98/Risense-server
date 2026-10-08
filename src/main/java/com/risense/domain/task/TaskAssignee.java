package com.risense.domain.task;

import com.risense.domain.member.ProjectMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task_assignees")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAssignee {

    public static TaskAssignee assign(Task task, ProjectMember member, OffsetDateTime now) {
        TaskAssignee assignment = new TaskAssignee();
        assignment.task = task;
        assignment.member = member;
        assignment.id = new TaskAssigneeId(task.getId(), member.getId());
        assignment.assignedAt = now;
        return assignment;
    }

    @EmbeddedId
    private TaskAssigneeId id;

    @MapsId("taskId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_assignees_task"))
    private Task task;

    @MapsId("memberId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_assignees_member"))
    private ProjectMember member;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt;
}
