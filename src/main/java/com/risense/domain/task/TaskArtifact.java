package com.risense.domain.task;

import com.risense.domain.member.ProjectMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task_artifacts")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskArtifact {

    public static TaskArtifact create(Task task, String title, String url, ProjectMember creator, OffsetDateTime now) {
        TaskArtifact artifact = new TaskArtifact();
        artifact.task = task;
        artifact.title = title;
        artifact.url = url;
        artifact.createdBy = creator;
        artifact.createdAt = now;
        return artifact;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_artifacts_task"))
    private Task task;

    @Column(name = "title", nullable = true, length = 100)
    private String title;

    @Column(name = "url", nullable = false, length = 500)
    private String url;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, foreignKey = @ForeignKey(name = "fk_artifacts_member"))
    private ProjectMember createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
