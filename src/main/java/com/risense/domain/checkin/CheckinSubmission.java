package com.risense.domain.checkin;

import com.risense.domain.member.ProjectMember;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 멤버별 회차 제출. 마감 후 제출이면 is_late=true */
@Entity
@Table(
    name = "checkin_submission",
    uniqueConstraints = @UniqueConstraint(columnNames = {"round_id", "member_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CheckinSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "round_id")
    private CheckinRound round;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private ProjectMember member;

    @Column(name = "is_late", nullable = false)
    private boolean late = false;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
