package com.teamplay.riskradar.domain.checkin;

import com.teamplay.riskradar.domain.task.Task;
import com.teamplay.riskradar.domain.task.TaskStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 체크인 내 작업별 업데이트. 최소 1개 이상이면 제출 가능. */
@Entity
@Table(
    name = "checkin_task_update",
    uniqueConstraints = @UniqueConstraint(columnNames = {"submission_id", "task_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckinTaskUpdate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "submission_id")
    private CheckinSubmission submission;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id")
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskStatus status;

    /** 0 / 25 / 50 / 75 / 100 */
    @Column(nullable = false)
    private short progress;

    @Enumerated(EnumType.STRING)
    @Column(name = "blocked_reason", length = 30)
    private BlockedReason blockedReason;

    @Column(name = "request_to_team", columnDefinition = "text")
    private String requestToTeam;

    @Column(name = "next_action", columnDefinition = "text")
    private String nextAction;
}
