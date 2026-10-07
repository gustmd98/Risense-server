package com.risense.domain.member;

import com.risense.domain.account.Account;
import com.risense.domain.project.Project;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 프로젝트 멤버십. 역할/가입상태가 여기에 있어
 * 한 계정이 프로젝트마다 다른 역할을 가질 수 있다.
 * 탈퇴/내보내기 후에도 행은 보존(기록에 '탈퇴한 팀원' 표시).
 */
@Entity
@Table(
    name = "project_member",
    uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "account_id"})
)
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MemberStatus status;

    @Column(name = "requested_at")
    private OffsetDateTime requestedAt;

    @Column(name = "joined_at")
    private OffsetDateTime joinedAt;

    @Column(name = "left_at")
    private OffsetDateTime leftAt;
}
