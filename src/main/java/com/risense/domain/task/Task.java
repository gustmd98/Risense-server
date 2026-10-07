package com.risense.domain.task;

import com.risense.domain.project.Project;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_tasks_project"))
    private Project project;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "size", nullable = false, length = 2)
    private TaskSize size;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "progress", nullable = false)
    private Integer progress;

    @Column(name = "due_date", nullable = true)
    private LocalDate dueDate;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "cancelled_at", nullable = true)
    private OffsetDateTime cancelledAt;

    @Column(name = "completed_at", nullable = true)
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
