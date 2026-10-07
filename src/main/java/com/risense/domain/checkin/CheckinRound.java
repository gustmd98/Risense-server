package com.risense.domain.checkin;

import com.risense.domain.project.Project;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 체크인 회차. 누락 = 이 회차에 대한 submission 행이 없는 상태 */
@Entity
@Table(
    name = "checkin_round",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "scheduled_date"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckinRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "scheduled_date", nullable = false)
    private LocalDate scheduledDate;

    @Column(name = "deadline_at", nullable = false)
    private OffsetDateTime deadlineAt;
}
