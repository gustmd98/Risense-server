package com.teamplay.riskradar.domain.task;

import com.teamplay.riskradar.domain.member.ProjectMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 하위 작업. 작성자(담당 팀원)만, 또는 팀장이 수정/삭제 */
@Entity
@Table(name = "sub_task")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by")
    private ProjectMember createdBy;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(name = "is_done", nullable = false)
    private boolean done = false;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
