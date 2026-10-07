package com.risense.domain.invite;

import com.risense.domain.project.Project;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 재생성 시 기존 링크는 is_active=false 로 비활성화 */
@Entity
@Table(name = "invite_link")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(nullable = false, unique = true, length = 64)
    private String token;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "deactivated_at")
    private OffsetDateTime deactivatedAt;
}
