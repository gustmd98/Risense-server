package com.risense.domain.checkin;

import com.risense.domain.project.Project;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 프로젝트 체크인 요일 (frequency 수만큼) */
@Entity
@Table(
    name = "checkin_schedule",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "day_of_week"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckinSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 3)
    private CheckinDay dayOfWeek;
}
