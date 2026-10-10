package com.risense.project;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.risense.api.error.ApiException;
import com.risense.domain.member.*;
import com.risense.domain.project.*;
import com.risense.domain.user.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

class ProjectServiceTest {
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectMemberRepository members = mock(ProjectMemberRepository.class);
    private final ProjectCheckinDayRepository days = mock(ProjectCheckinDayRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-08T00:00:00Z"), ZoneOffset.UTC);
    private final ProjectAccess access = new ProjectAccess(projects, members);
    private final ProjectService service = new ProjectService(projects, members, days, users, access, clock, mock(com.risense.checkin.CheckinLifecycle.class));
    private User user;
    private Project project;
    private ProjectMember membership;

    @BeforeEach
    void setup() {
        user = User.register("test@example.com", "hash", "테스터", now());
        user.setId(7L);
        project = Project.create(user, now());
        project.setId(10L);
        membership = ProjectMember.leader(project, user, now());
        when(projects.findById(10L)).thenReturn(Optional.of(project));
        when(projects.findLockedById(10L)).thenReturn(Optional.of(project));
        when(members.findByProject_IdAndUser_Id(10L, 7L)).thenReturn(Optional.of(membership));
        when(days.findById_ProjectId(10L)).thenReturn(List.of());
    }

    @Test
    void creationAssignsApprovedLeaderAndStoresSchedule() {
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(projects.saveAndFlush(any(Project.class))).thenAnswer(call -> {
            Project saved = call.getArgument(0); saved.setId(22L); return saved;
        });
        var result = service.create(7L, request(List.of(CheckinDay.WED, CheckinDay.MON), 2));
        assertThat(result.id()).isEqualTo(22L);
        assertThat(result.status()).isEqualTo(ProjectStatus.IN_PROGRESS);
        assertThat(result.myRole()).isEqualTo(MemberRole.LEADER);
        assertThat(result.checkinDays()).containsExactly(CheckinDay.MON, CheckinDay.WED);
        var member = ArgumentCaptor.forClass(ProjectMember.class);
        verify(members).save(member.capture());
        assertThat(member.getValue().getJoinStatus()).isEqualTo(MemberStatus.APPROVED);
        assertThat(member.getValue().getJoinedAt()).isEqualTo(now());
        verify(days).saveAll(argThat(values -> {
            int count = 0;
            for (var value : values) {
                if (!Objects.equals(value.getId().getProjectId(), 22L)) return false;
                count++;
            }
            return count == 2;
        }));
    }

    @Test
    void duplicateDaysAndFrequencyMismatchDoNotWrite() {
        assertError(() -> service.create(7L, request(List.of(CheckinDay.MON, CheckinDay.MON), 2)),
                HttpStatus.BAD_REQUEST, "INVALID_CHECKIN_SCHEDULE");
        assertError(() -> service.create(7L, request(List.of(CheckinDay.MON), 2)),
                HttpStatus.BAD_REQUEST, "INVALID_CHECKIN_SCHEDULE");
        verifyNoInteractions(users);
        verify(projects, never()).saveAndFlush(any());
    }

    @Test
    void missingUserCannotCreate() {
        assertError(() -> service.create(7L, request(List.of(CheckinDay.MON), 1)),
                HttpStatus.UNAUTHORIZED, "UNAUTHORIZED");
        verify(projects, never()).saveAndFlush(any());
    }

    @Test
    void pendingRemovedAndNonmemberCannotRead() {
        for (var status : List.of(MemberStatus.PENDING, MemberStatus.REJECTED, MemberStatus.REMOVED)) {
            membership.setJoinStatus(status);
            assertError(() -> service.detail(10L, 7L), HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED");
        }
        when(members.findByProject_IdAndUser_Id(10L, 7L)).thenReturn(Optional.empty());
        assertError(() -> service.detail(10L, 7L), HttpStatus.FORBIDDEN, "PROJECT_ACCESS_DENIED");
    }

    @Test
    void ordinaryMemberCanReadButCannotUpdateOrClose() {
        membership.setRole(MemberRole.MEMBER);
        assertThat(service.detail(10L, 7L).myRole()).isEqualTo(MemberRole.MEMBER);
        assertError(() -> service.update(10L, 7L, request(List.of(CheckinDay.MON), 1)),
                HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        assertError(() -> service.close(10L, 7L), HttpStatus.FORBIDDEN, "PROJECT_MANAGER_REQUIRED");
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.IN_PROGRESS);
    }

    @Test
    void coleaderCanUpdateScheduleWithoutReplacingUnchangedCompositeKey() {
        membership.setRole(MemberRole.CO_LEADER);
        var monday = ProjectCheckinDay.of(project, CheckinDay.MON);
        var friday = ProjectCheckinDay.of(project, CheckinDay.FRI);
        when(days.findById_ProjectId(10L)).thenReturn(List.of(monday, friday));
        var result = service.update(10L, 7L, request(List.of(CheckinDay.WED, CheckinDay.MON), 2));
        assertThat(result.checkinDays()).containsExactly(CheckinDay.MON, CheckinDay.WED);
        assertThat(result.updatedAt()).isEqualTo(now());
        verify(days).delete(friday);
        verify(days, never()).delete(monday);
        verify(days).saveAll(argThat(values -> {
            var iterator = values.iterator();
            return iterator.hasNext() && iterator.next().getId().getDayOfWeek() == CheckinDay.WED && !iterator.hasNext();
        }));
    }

    @Test
    void doneAndClosedProjectsRejectSettingsButRemainReadable() {
        for (var status : List.of(ProjectStatus.DONE, ProjectStatus.CLOSED)) {
            project.setStatus(status);
            assertError(() -> service.update(10L, 7L, request(List.of(CheckinDay.MON), 1)),
                    HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE");
            assertThat(service.detail(10L, 7L).status()).isEqualTo(status);
        }
        verify(days, never()).delete(any());
        verify(days, never()).saveAll(any());
    }

    @Test
    void closeIsIdempotentAndPreservesOriginalTimestamp() {
        membership.setRole(MemberRole.CO_LEADER);
        assertThat(service.close(10L, 7L).closedAt()).isEqualTo(now());
        var original = now().minusDays(1);
        project.setClosedAt(original);
        assertThat(service.close(10L, 7L).closedAt()).isEqualTo(original);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.CLOSED);
    }

    @Test
    void unknownProjectReturns404() {
        assertError(() -> service.detail(99L, 7L), HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND");
        assertError(() -> service.close(99L, 7L), HttpStatus.NOT_FOUND, "PROJECT_NOT_FOUND");
    }

    @Test
    void listReadsApprovedMembershipsAndBatchesDays() {
        project.setStatus(ProjectStatus.CLOSED);
        when(members.findMemberships(7L, MemberStatus.APPROVED)).thenReturn(List.of(membership));
        when(days.findById_ProjectIdIn(List.of(10L))).thenReturn(List.of(ProjectCheckinDay.of(project, CheckinDay.MON)));
        var results = service.list(7L);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().status()).isEqualTo(ProjectStatus.CLOSED);
        assertThat(results.getFirst().checkinDays()).containsExactly(CheckinDay.MON);
        verify(days, never()).findById_ProjectId(anyLong());
    }

    @Test
    void emptyListIsAnArrayWithoutScheduleQuery() {
        assertThat(service.list(7L)).isEmpty();
        verify(days, never()).findById_ProjectIdIn(any());
    }

    private ProjectRequest request(List<CheckinDay> selected, int frequency) {
        return new ProjectRequest(" 프로젝트 ", " 수업 ", LocalDate.of(2026, 12, 20),
                LocalTime.of(23, 0), frequency, selected);
    }

    private OffsetDateTime now() { return OffsetDateTime.now(clock); }

    private void assertError(org.assertj.core.api.ThrowableAssert.ThrowingCallable call,
            HttpStatus status, String code) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, error -> {
            assertThat(error.getStatus()).isEqualTo(status);
            assertThat(error.getCode()).isEqualTo(code);
        });
    }
}
