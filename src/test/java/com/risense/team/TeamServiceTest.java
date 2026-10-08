package com.risense.team;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.risense.api.error.ApiException;
import com.risense.domain.invite.*;
import com.risense.domain.member.*;
import com.risense.domain.project.*;
import com.risense.domain.user.*;
import com.risense.project.ProjectAccess;
import com.risense.taskapi.TaskAssignmentService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class TeamServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final InviteLinkRepository invites = mock(InviteLinkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    private final ProjectAccess access = new ProjectAccess(projects, members);
    private final TaskAssignmentService assignments = mock(TaskAssignmentService.class);
    private final TeamService service = new TeamService(access, members, invites, users, clock, assignments);
    private final String token = "a".repeat(64);
    private Project project;
    private ProjectMember actor;
    private ProjectMember target;
    private InviteLink link;

    @BeforeEach
    void setup() {
        var leader = User.register("leader@example.com", "hash", "팀장", now()); leader.setId(1L);
        var applicant = User.register("member@example.com", "hash", "팀원", now()); applicant.setId(2L);
        project = Project.create(leader, now()); project.setId(10L); project.setTitle("프로젝트");
        actor = ProjectMember.leader(project, leader, now()); actor.setId(1L);
        target = ProjectMember.request(project, applicant, now().minusHours(1)); target.setId(2L);
        link = InviteLink.issue(project, actor, token, now(), now().plusHours(1)); link.setId(3L);
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(projects.findLockedById(10L)).thenReturn(Optional.of(project));
        when(members.findByProject_IdAndUser_Id(10L, 1L)).thenReturn(Optional.of(actor));
        when(members.findByIdAndProject_Id(2L, 10L)).thenReturn(Optional.of(target));
        when(members.countByProject_IdAndJoinStatusAndRole(10L, MemberStatus.APPROVED, MemberRole.LEADER)).thenReturn(1L);
        when(invites.findByToken(token)).thenReturn(Optional.of(link));
        when(invites.findProjectIdByToken(token)).thenReturn(Optional.of(10L));
        when(invites.findByIdAndProject_Id(3L, 10L)).thenReturn(Optional.of(link));
        when(users.findById(2L)).thenReturn(Optional.of(applicant));
    }

    @Test
    void issueRotatesOldLinkAndProduces256bitToken() {
        when(invites.findByProject_IdAndIsActiveTrue(10L)).thenReturn(List.of(link));
        when(invites.saveAndFlush(any())).thenAnswer(call -> { InviteLink saved = call.getArgument(0); saved.setId(4L); return saved; });
        var result = service.issue(10L, 1L, null);
        assertThat(link.getIsActive()).isFalse();
        assertThat(result.token()).matches("[0-9a-f]{64}").isNotEqualTo(token);
        assertThat(result.expiresAt()).isEqualTo(now().plusHours(168));
        var saved = ArgumentCaptor.forClass(InviteLink.class);
        verify(invites).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getCreatedBy()).isSameAs(actor);
    }

    @Test
    void issueRejectsExpiryOutsideAllowedRange() {
        for (int hours : List.of(0, 721)) {
            error(() -> service.issue(10L, 1L, new TeamRequests.Invite(hours)), HttpStatus.BAD_REQUEST, "INVALID_INVITE_EXPIRY");
        }
        verify(invites, never()).saveAndFlush(any());
    }

    @Test
    void previewExposesOnlyProjectTitleAndExpiry() {
        var result = service.preview(token);
        assertThat(result.projectId()).isEqualTo(10L);
        assertThat(result.projectTitle()).isEqualTo("프로젝트");
        verifyNoInteractions(users);
    }

    @Test
    void expiresAtExactBoundaryAndInactiveLinksAreGone() {
        link.setExpiresAt(now());
        error(() -> service.preview(token), HttpStatus.GONE, "INVITE_UNAVAILABLE");
        error(() -> service.join(token, 2L), HttpStatus.GONE, "INVITE_UNAVAILABLE");
        link.setExpiresAt(now().plusHours(1)); link.revoke();
        error(() -> service.preview(token), HttpStatus.GONE, "INVITE_UNAVAILABLE");
        verify(members, never()).saveAndFlush(any());
    }

    @Test
    void invalidOrUnknownTokenIsNotFound() {
        error(() -> service.preview("bad"), HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND");
        error(() -> service.join("b".repeat(64), 2L), HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND");
    }

    @Test
    void revokeIsIdempotentAndScopedToProject() {
        service.revoke(10L, 3L, 1L); service.revoke(10L, 3L, 1L);
        assertThat(link.getIsActive()).isFalse();
        error(() -> service.revoke(10L, 99L, 1L), HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND");
    }

    @Test
    void currentInviteSkipsExpiredLinks() {
        link.setExpiresAt(now());
        when(invites.findByProject_IdAndIsActiveTrue(10L)).thenReturn(List.of(link));
        error(() -> service.currentInvite(10L, 1L), HttpStatus.NOT_FOUND, "INVITE_NOT_FOUND");
    }

    @Test
    void joinCreatesPendingMemberAndChecksValidityAfterProjectLock() {
        when(members.saveAndFlush(any())).thenAnswer(call -> { ProjectMember saved = call.getArgument(0); saved.setId(2L); return saved; });
        var result = service.join(token, 2L);
        assertThat(result.joinStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(result.role()).isEqualTo(MemberRole.MEMBER);
        assertThat(result.joinedAt()).isNull();
        var order = inOrder(invites, projects);
        order.verify(invites).findProjectIdByToken(token);
        order.verify(projects).findLockedById(10L);
        order.verify(invites).findByToken(token);
    }

    @Test
    void pendingAndApprovedJoinRetriesPreserveRoleAndTimestamps() {
        when(members.findByProject_IdAndUser_Id(10L, 2L)).thenReturn(Optional.of(target));
        var requested = target.getRequestedAt();
        assertThat(service.join(token, 2L).requestedAt()).isEqualTo(requested);
        target.approve(now().minusMinutes(30)); target.setRole(MemberRole.CO_LEADER);
        var result = service.join(token, 2L);
        assertThat(result.role()).isEqualTo(MemberRole.CO_LEADER);
        assertThat(result.joinedAt()).isEqualTo(now().minusMinutes(30));
        verify(members, never()).saveAndFlush(any());
    }

    @Test
    void rejectedCanReapplyButRemovedCannot() {
        when(members.findByProject_IdAndUser_Id(10L, 2L)).thenReturn(Optional.of(target));
        target.reject();
        assertThat(service.join(token, 2L).joinStatus()).isEqualTo(MemberStatus.PENDING);
        assertThat(target.getRequestedAt()).isEqualTo(now());
        target.remove(now());
        error(() -> service.join(token, 2L), HttpStatus.FORBIDDEN, "MEMBER_REMOVED");
    }

    @Test
    void missingUserCannotJoin() {
        when(users.findById(2L)).thenReturn(Optional.empty());
        error(() -> service.join(token, 2L), HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
    }

    @Test
    void approvalAndRejectionHaveStatusGuards() {
        assertThat(service.approve(10L, 2L, 1L).joinStatus()).isEqualTo(MemberStatus.APPROVED);
        target.setJoinedAt(now().minusHours(2));
        assertThat(service.approve(10L, 2L, 1L).joinedAt()).isEqualTo(now().minusHours(2));
        error(() -> service.reject(10L, 2L, 1L), HttpStatus.CONFLICT, "INVALID_MEMBER_STATUS");
        target.request(now());
        assertThat(service.reject(10L, 2L, 1L).joinStatus()).isEqualTo(MemberStatus.REJECTED);
        error(() -> service.approve(10L, 2L, 1L), HttpStatus.CONFLICT, "INVALID_MEMBER_STATUS");
    }

    @Test
    void ordinaryMemberCannotManageAndPendingCannotReadTeam() {
        actor.setRole(MemberRole.MEMBER);
        error(() -> service.approve(10L, 2L, 1L), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        error(() -> service.issue(10L, 1L, null), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        error(() -> service.pending(10L, 1L), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        actor.setJoinStatus(MemberStatus.PENDING);
        error(() -> service.list(10L, 1L), HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED");
    }

    @Test
    void coleaderCanApproveAndRemoveOrdinaryMemberButCannotChangeRolesOrRemoveManagers() {
        actor.setRole(MemberRole.CO_LEADER);
        service.approve(10L, 2L, 1L);
        error(() -> service.changeRole(10L, 2L, 1L, MemberRole.CO_LEADER), HttpStatus.FORBIDDEN, "LEADER_REQUIRED");
        target.setRole(MemberRole.LEADER);
        error(() -> service.remove(10L, 2L, 1L), HttpStatus.FORBIDDEN, "LEADER_REQUIRED");
        target.setRole(MemberRole.CO_LEADER);
        error(() -> service.remove(10L, 2L, 1L), HttpStatus.FORBIDDEN, "LEADER_REQUIRED");
        target.setRole(MemberRole.MEMBER);
        assertThat(service.remove(10L, 2L, 1L).joinStatus()).isEqualTo(MemberStatus.REMOVED);
    }

    @Test
    void lastLeaderCannotBeDemotedOrRemovedIncludingSelf() {
        when(members.findByIdAndProject_Id(1L, 10L)).thenReturn(Optional.of(actor));
        error(() -> service.changeRole(10L, 1L, 1L, MemberRole.MEMBER), HttpStatus.CONFLICT, "LAST_LEADER");
        error(() -> service.remove(10L, 1L, 1L), HttpStatus.CONFLICT, "LAST_LEADER");
        assertThat(actor.getRole()).isEqualTo(MemberRole.LEADER);
        assertThat(actor.getJoinStatus()).isEqualTo(MemberStatus.APPROVED);
        assertThat(service.changeRole(10L, 1L, 1L, MemberRole.LEADER).role()).isEqualTo(MemberRole.LEADER);
    }

    @Test
    void anotherLeaderAllowsDemotionAndLockPrecedesLeaderCount() {
        target.approve(now()); target.setRole(MemberRole.LEADER);
        when(members.countByProject_IdAndJoinStatusAndRole(10L, MemberStatus.APPROVED, MemberRole.LEADER)).thenReturn(2L);
        assertThat(service.changeRole(10L, 2L, 1L, MemberRole.MEMBER).role()).isEqualTo(MemberRole.MEMBER);
        var order = inOrder(projects, members);
        order.verify(projects).findLockedById(10L);
        order.verify(members).countByProject_IdAndJoinStatusAndRole(10L, MemberStatus.APPROVED, MemberRole.LEADER);
    }

    @Test
    void pendingRoleChangeAndCrossProjectTargetAreRejected() {
        error(() -> service.changeRole(10L, 2L, 1L, MemberRole.CO_LEADER), HttpStatus.CONFLICT, "INVALID_MEMBER_STATUS");
        error(() -> service.approve(10L, 999L, 1L), HttpStatus.NOT_FOUND, "MEMBER_NOT_FOUND");
    }

    @Test
    void removalPreservesMembershipAndRevokesIssuerLinks() {
        target.approve(now());
        var issued = InviteLink.issue(project, target, "b".repeat(64), now(), now().plusHours(1));
        when(invites.findByCreatedBy_IdAndIsActiveTrue(2L)).thenReturn(List.of(issued));
        var joinedAt = target.getJoinedAt();
        var result = service.remove(10L, 2L, 1L);
        assertThat(result.joinStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThat(result.removedAt()).isEqualTo(now());
        assertThat(result.joinedAt()).isEqualTo(joinedAt);
        assertThat(issued.getIsActive()).isFalse();
        verify(assignments).removeMember(target, actor, now());
        verify(members, never()).delete(any());
        assertThat(service.remove(10L, 2L, 1L).removedAt()).isEqualTo(now());
    }

    @Test
    void closedAndDoneProjectsBlockEveryWriteButAllowOwnStatus() {
        when(members.findByProject_IdAndUser_Id(10L, 2L)).thenReturn(Optional.of(target));
        for (var status : List.of(ProjectStatus.CLOSED, ProjectStatus.DONE)) {
            project.setStatus(status);
            error(() -> service.issue(10L, 1L, null), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.revoke(10L, 3L, 1L), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.join(token, 2L), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.approve(10L, 2L, 1L), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.reject(10L, 2L, 1L), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.changeRole(10L, 2L, 1L, MemberRole.MEMBER), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            error(() -> service.remove(10L, 2L, 1L), HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            assertThat(service.myMembership(10L, 2L).joinStatus()).isEqualTo(MemberStatus.PENDING);
        }
    }

    @Test
    void listsUseSeparateApprovedAndPendingQueries() {
        when(members.findTeam(10L, MemberStatus.APPROVED)).thenReturn(List.of(actor));
        when(members.findTeam(10L, MemberStatus.PENDING)).thenReturn(List.of(target));
        assertThat(service.list(10L, 1L).getFirst().id()).isEqualTo(1L);
        assertThat(service.pending(10L, 1L).getFirst().id()).isEqualTo(2L);
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }
    private void error(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }
}
