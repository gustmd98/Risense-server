package com.risense.team;

import com.risense.api.error.ApiException;
import com.risense.domain.invite.*;
import com.risense.domain.member.*;
import com.risense.domain.project.*;
import com.risense.domain.user.UserRepository;
import com.risense.project.ProjectAccess;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {
    private final ProjectAccess access;
    private final ProjectMemberRepository members;
    private final InviteLinkRepository invites;
    private final UserRepository users;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TeamService(ProjectAccess access, ProjectMemberRepository members, InviteLinkRepository invites,
            UserRepository users, Clock clock) {
        this.access = access;
        this.members = members;
        this.invites = invites;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public InviteResponse issue(long projectId, long userId, TeamRequests.Invite request) {
        var project = access.lockedProject(projectId);
        var actor = access.manager(projectId, userId);
        access.requireWritable(project);
        int hours = request == null ? 168 : request.expiresInHours();
        if (hours < 1 || hours > 720) throw error(HttpStatus.BAD_REQUEST, "INVALID_INVITE_EXPIRY", "만료 시간은 1~720시간이어야 합니다.");
        invites.findByProject_IdAndIsActiveTrue(projectId).forEach(InviteLink::revoke);
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        var now = now();
        var link = InviteLink.issue(project, actor, HexFormat.of().formatHex(bytes), now, now.plusHours(hours));
        return InviteResponse.from(invites.saveAndFlush(link));
    }

    @Transactional(readOnly = true)
    public InviteResponse currentInvite(long projectId, long userId) {
        var project = access.project(projectId);
        access.manager(projectId, userId);
        access.requireWritable(project);
        return invites.findByProject_IdAndIsActiveTrue(projectId).stream()
                .filter(this::notExpired).findFirst().map(InviteResponse::from)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND", "유효한 초대 링크가 없습니다."));
    }

    @Transactional
    public void revoke(long projectId, long inviteId, long userId) {
        var project = access.lockedProject(projectId);
        access.manager(projectId, userId);
        access.requireWritable(project);
        var link = invites.findByIdAndProject_Id(inviteId, projectId).orElseThrow(TeamService::inviteNotFound);
        link.revoke();
    }

    @Transactional(readOnly = true)
    public InvitePreview preview(String token) {
        var link = validLink(token);
        access.requireWritable(link.getProject());
        return new InvitePreview(link.getProject().getId(), link.getProject().getTitle(), link.getExpiresAt());
    }

    @Transactional
    public MemberResponse join(String token, long userId) {
        validateToken(token);
        long projectId = invites.findProjectIdByToken(token).orElseThrow(TeamService::inviteNotFound);
        var project = access.lockedProject(projectId);
        // Read validity only after acquiring the same lock used for revocation/rotation/closure.
        validLink(token);
        access.requireWritable(project);
        var user = users.findById(userId).orElseThrow(() -> error(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해주세요."));
        var existing = members.findByProject_IdAndUser_Id(projectId, userId);
        if (existing.isPresent()) {
            var member = existing.get();
            if (member.getJoinStatus() == MemberStatus.REMOVED) {
                throw error(HttpStatus.FORBIDDEN, "MEMBER_REMOVED", "내보내진 프로젝트에는 다시 가입 요청할 수 없습니다.");
            }
            if (member.getJoinStatus() == MemberStatus.REJECTED) member.request(now());
            return MemberResponse.from(member); // PENDING/APPROVED retries preserve state and timestamps.
        }
        return MemberResponse.from(members.saveAndFlush(ProjectMember.request(project, user, now())));
    }

    @Transactional(readOnly = true)
    public MemberResponse myMembership(long projectId, long userId) {
        access.project(projectId);
        return members.findByProject_IdAndUser_Id(projectId, userId).map(MemberResponse::from)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "프로젝트 가입 내역이 없습니다."));
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> list(long projectId, long userId) {
        access.project(projectId);
        access.approvedMember(projectId, userId);
        return members.findTeam(projectId, MemberStatus.APPROVED).stream().map(MemberResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<MemberResponse> pending(long projectId, long userId) {
        access.project(projectId);
        access.manager(projectId, userId);
        return members.findTeam(projectId, MemberStatus.PENDING).stream().map(MemberResponse::from).toList();
    }

    @Transactional
    public MemberResponse approve(long projectId, long memberId, long userId) {
        writableManager(projectId, userId);
        var member = target(projectId, memberId);
        if (member.getJoinStatus() == MemberStatus.APPROVED) return MemberResponse.from(member);
        requireStatus(member, MemberStatus.PENDING);
        member.approve(now());
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse reject(long projectId, long memberId, long userId) {
        writableManager(projectId, userId);
        var member = target(projectId, memberId);
        if (member.getJoinStatus() == MemberStatus.REJECTED) return MemberResponse.from(member);
        requireStatus(member, MemberStatus.PENDING);
        member.reject();
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse changeRole(long projectId, long memberId, long userId, MemberRole role) {
        var actor = writableManager(projectId, userId);
        if (actor.getRole() != MemberRole.LEADER) {
            throw error(HttpStatus.FORBIDDEN, "LEADER_REQUIRED", "역할 변경은 팀장만 할 수 있습니다.");
        }
        if (role == null) throw error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "역할을 선택해주세요.");
        var member = target(projectId, memberId);
        requireStatus(member, MemberStatus.APPROVED);
        if (role != MemberRole.LEADER) protectLastLeader(projectId, member);
        member.setRole(role);
        return MemberResponse.from(member);
    }

    @Transactional
    public MemberResponse remove(long projectId, long memberId, long userId) {
        var actor = writableManager(projectId, userId);
        var member = target(projectId, memberId);
        if (actor.getRole() == MemberRole.CO_LEADER && member.getRole() != MemberRole.MEMBER) {
            throw error(HttpStatus.FORBIDDEN, "LEADER_REQUIRED", "팀장·공동 팀장 내보내기는 팀장만 할 수 있습니다.");
        }
        if (member.getJoinStatus() == MemberStatus.REMOVED) return MemberResponse.from(member);
        requireStatus(member, MemberStatus.APPROVED);
        protectLastLeader(projectId, member);
        member.remove(now());
        invites.findByCreatedBy_IdAndIsActiveTrue(memberId).forEach(InviteLink::revoke);
        return MemberResponse.from(member);
    }

    private ProjectMember writableManager(long projectId, long userId) {
        var project = access.lockedProject(projectId);
        var actor = access.manager(projectId, userId);
        access.requireWritable(project);
        return actor;
    }

    private ProjectMember target(long projectId, long memberId) {
        return members.findByIdAndProject_Id(memberId, projectId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND", "프로젝트 팀원을 찾을 수 없습니다."));
    }

    private void requireStatus(ProjectMember member, MemberStatus status) {
        if (member.getJoinStatus() != status) throw error(HttpStatus.CONFLICT, "INVALID_MEMBER_STATUS", "현재 가입 상태에서 처리할 수 없습니다.");
    }

    private void protectLastLeader(long projectId, ProjectMember member) {
        if (member.getRole() == MemberRole.LEADER
                && members.countByProject_IdAndJoinStatusAndRole(projectId, MemberStatus.APPROVED, MemberRole.LEADER) <= 1) {
            throw error(HttpStatus.CONFLICT, "LAST_LEADER", "프로젝트에 팀장이 최소 한 명 있어야 합니다.");
        }
    }

    private InviteLink validLink(String token) {
        validateToken(token);
        var link = invites.findByToken(token).orElseThrow(TeamService::inviteNotFound);
        if (!Boolean.TRUE.equals(link.getIsActive()) || !notExpired(link)) {
            throw error(HttpStatus.GONE, "INVITE_UNAVAILABLE", "만료되었거나 비활성화된 초대 링크입니다.");
        }
        return link;
    }

    private boolean notExpired(InviteLink link) {
        return link.getExpiresAt() == null || link.getExpiresAt().isAfter(now());
    }

    private void validateToken(String token) {
        if (token == null || !token.matches("[0-9a-f]{64}")) throw inviteNotFound();
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private static ApiException inviteNotFound() { return error(HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND", "초대 링크를 찾을 수 없습니다."); }
    private static ApiException error(HttpStatus status, String code, String message) { return new ApiException(status, code, message); }
}
