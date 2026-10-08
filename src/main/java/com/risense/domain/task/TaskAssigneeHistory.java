package com.risense.domain.task;

import com.risense.domain.member.ProjectMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task_assignee_histories")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAssigneeHistory {

    public static TaskAssigneeHistory record(Task task, ProjectMember member, AssigneeAction action,
            ProjectMember actor, OffsetDateTime now) {
        TaskAssigneeHistory history = new TaskAssigneeHistory();
        history.task = task;
        history.member = member;
        history.action = action;
        history.changedBy = actor;
        history.changedAt = now;
        return history;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_hist_task"))
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_hist_member"))
    private ProjectMember member;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 10)
    private AssigneeAction action;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false, foreignKey = @ForeignKey(name = "fk_hist_changer"))
    private ProjectMember changedBy;

    @Column(name = "changed_at", nullable = false)
    private OffsetDateTime changedAt;
}
