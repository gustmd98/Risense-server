package com.risense.issue;

import com.risense.domain.task.Task;
import com.risense.domain.member.ProjectMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.*;

@Entity
@Table(name = "task_issues")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskIssue implements org.springframework.data.domain.Persistable<Long> {
    @Transient private boolean fresh = true;
    @Override public Long getId() { return taskId; }
    @Override public boolean isNew() { return fresh; }
    @PostLoad @PostPersist private void persisted() { fresh = false; }
    @Id private Long taskId;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @MapsId @JoinColumn(name = "task_id")
    private Task task;
    @Column(nullable = false) private boolean open;
    @Column(columnDefinition = "text") private String content;
    @Column(nullable = false) private long revision;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "reported_by") private ProjectMember reportedBy;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "resolved_by") private ProjectMember resolvedBy;
    private OffsetDateTime reportedAt;
    private OffsetDateTime resolvedAt;

    public static TaskIssue create(Task task) {
        var issue = new TaskIssue(); issue.task = task; issue.taskId = task.getId(); return issue;
    }
    public void report(String content, ProjectMember actor, OffsetDateTime now) {
        this.content = content; open = true; reportedBy = actor; reportedAt = now;
        resolvedBy = null; resolvedAt = null; revision++;
    }
    public void resolve(ProjectMember actor, OffsetDateTime now) {
        if (!open) return;
        open = false; resolvedBy = actor; resolvedAt = now; revision++;
    }
}
