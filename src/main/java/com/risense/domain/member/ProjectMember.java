package com.risense.domain.member;

import com.risense.domain.project.Project;
import com.risense.domain.user.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_members", uniqueConstraints = {@UniqueConstraint(name = "uk_project_members", columnNames = {"project_id", "user_id"})})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMember {

    public static ProjectMember request(Project project, User user, OffsetDateTime now) {
        ProjectMember member = new ProjectMember();
        member.project = project;
        member.user = user;
        member.request(now);
        return member;
    }

    public void request(OffsetDateTime now) {
        role = MemberRole.MEMBER;
        joinStatus = MemberStatus.PENDING;
        requestedAt = now;
        joinedAt = null;
        removedAt = null;
    }

    public void approve(OffsetDateTime now) {
        joinStatus = MemberStatus.APPROVED;
        joinedAt = now;
    }

    public void reject() { joinStatus = MemberStatus.REJECTED; }

    public void remove(OffsetDateTime now) {
        joinStatus = MemberStatus.REMOVED;
        removedAt = now;
    }

    public static ProjectMember leader(Project project, User user, OffsetDateTime now) {
        ProjectMember member = new ProjectMember();
        member.project = project;
        member.user = user;
        member.role = MemberRole.LEADER;
        member.joinStatus = MemberStatus.APPROVED;
        member.requestedAt = now;
        member.joinedAt = now;
        return member;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_members_project"))
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_members_user"))
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private MemberRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_status", nullable = false, length = 20)
    private MemberStatus joinStatus;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "joined_at", nullable = true)
    private OffsetDateTime joinedAt;

    @Column(name = "removed_at", nullable = true)
    private OffsetDateTime removedAt;
}
