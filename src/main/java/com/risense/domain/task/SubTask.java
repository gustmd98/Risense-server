package com.risense.domain.task;

import com.risense.domain.member.ProjectMember;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sub_tasks")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, foreignKey = @ForeignKey(name = "fk_subtasks_task"))
    private Task task;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "completed", nullable = false)
    private Boolean completed;

    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "assignee_member_id", nullable = true, foreignKey = @ForeignKey(name = "fk_subtasks_member"))
    private ProjectMember assigneeMember;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
