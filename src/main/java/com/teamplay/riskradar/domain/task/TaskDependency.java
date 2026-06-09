package com.teamplay.riskradar.domain.task;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 선행 -> 후행. 순환 의존성 방지는 애플리케이션 레벨에서 검증 */
@Entity
@Table(
    name = "task_dependency",
    uniqueConstraints = @UniqueConstraint(columnNames = {"predecessor_id", "successor_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "predecessor_id")
    private Task predecessor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "successor_id")
    private Task successor;
}
