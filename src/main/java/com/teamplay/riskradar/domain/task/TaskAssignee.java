package com.teamplay.riskradar.domain.task;

import com.teamplay.riskradar.domain.member.ProjectMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 작업 담당자 (M:N). is_active=false 행은 담당 해제 이력으로 보존
 * → 최종 리포트의 '담당자 변경 기록'이 여기서 나온다.
 * 미배정 = 해당 작업에 active 담당자가 없는 상태.
 */
@Entity
@Table(name = "task_assignee")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskAssignee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private ProjectMember member;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "assigned_at", nullable = false)
    private OffsetDateTime assignedAt = OffsetDateTime.now();

    @Column(name = "unassigned_at")
    private OffsetDateTime unassignedAt;
}
