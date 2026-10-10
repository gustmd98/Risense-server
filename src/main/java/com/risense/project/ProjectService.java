package com.risense.project;

import com.risense.checkin.CheckinLifecycle;

import com.risense.api.error.ApiException;
import com.risense.domain.member.*;
import com.risense.domain.project.*;
import com.risense.domain.user.UserRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {
    private final CheckinLifecycle checkins;
    private final ProjectRepository projects;
    private final ProjectMemberRepository members;
    private final ProjectCheckinDayRepository days;
    private final UserRepository users;
    private final ProjectAccess access;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, ProjectMemberRepository members,
            ProjectCheckinDayRepository days, UserRepository users, ProjectAccess access, Clock clock, CheckinLifecycle checkins) {
        this.checkins = checkins;
        this.projects = projects;
        this.members = members;
        this.days = days;
        this.users = users;
        this.access = access;
        this.clock = clock;
    }

    @Transactional
    public ProjectResponse create(long userId, ProjectRequest request) {
        validateSchedule(request);
        var user = users.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해주세요."));
        var now = OffsetDateTime.now(clock);
        Project project = Project.create(user, now);
        applySettings(project, request, now);
        projects.saveAndFlush(project);
        members.save(ProjectMember.leader(project, user, now));
        days.saveAll(request.checkinDays().stream().map(day -> ProjectCheckinDay.of(project, day)).toList());
        checkins.initialize(project.getId());
        return ProjectResponse.from(project, MemberRole.LEADER, request.checkinDays());
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(long userId) {
        var memberships = members.findMemberships(userId, MemberStatus.APPROVED);
        if (memberships.isEmpty()) return List.of();
        var ids = memberships.stream().map(m -> m.getProject().getId()).toList();
        var byProject = days.findById_ProjectIdIn(ids).stream().collect(Collectors.groupingBy(
                day -> day.getId().getProjectId(),
                Collectors.mapping(day -> day.getId().getDayOfWeek(), Collectors.toList())));
        return memberships.stream().map(m -> ProjectResponse.from(m.getProject(), m.getRole(),
                byProject.getOrDefault(m.getProject().getId(), List.of()))).toList();
    }

    @Transactional(readOnly = true)
    public ProjectResponse detail(long projectId, long userId) {
        Project project = access.project(projectId);
        var member = access.approvedMember(projectId, userId);
        return response(project, member.getRole());
    }

    @Transactional
    public ProjectResponse update(long projectId, long userId, ProjectRequest request) {
        Project project = access.lockedProject(projectId);
        var member = access.manager(projectId, userId);
        access.requireWritable(project);
        validateSchedule(request);
        checkins.beforeChange(projectId);
        applySettings(project, request, OffsetDateTime.now(clock));
        // Update the difference, avoiding DELETE/INSERT conflicts for the same composite key.
        var existing = days.findById_ProjectId(projectId);
        Set<CheckinDay> selected = EnumSet.copyOf(request.checkinDays());
        Set<CheckinDay> previous = EnumSet.noneOf(CheckinDay.class);
        for (var day : existing) {
            previous.add(day.getId().getDayOfWeek());
            if (!selected.contains(day.getId().getDayOfWeek())) days.delete(day);
        }
        days.saveAll(selected.stream().filter(day -> !previous.contains(day))
                .map(day -> ProjectCheckinDay.of(project, day)).toList());
        checkins.changeSchedule(projectId, request.checkinDays());
        return ProjectResponse.from(project, member.getRole(), request.checkinDays());
    }

    @Transactional
    public ProjectResponse close(long projectId, long userId) {
        Project project = access.lockedProject(projectId);
        var member = access.manager(projectId, userId);
        // Retrying a close request preserves the original close timestamp.
        if (project.getStatus() != ProjectStatus.CLOSED) {
            checkins.beforeChange(projectId);
            project.close(OffsetDateTime.now(clock));
            checkins.closed(projectId);
        }
        return response(project, member.getRole());
    }

    private ProjectResponse response(Project project, MemberRole role) {
        return ProjectResponse.from(project, role, days.findById_ProjectId(project.getId()).stream()
                .map(day -> day.getId().getDayOfWeek()).toList());
    }

    private void validateSchedule(ProjectRequest request) {
        var selected = request.checkinDays();
        if (selected == null || selected.isEmpty() || selected.size() > 7 || selected.stream().anyMatch(Objects::isNull)
                || request.checkinFrequency() == null || selected.size() != request.checkinFrequency()
                || new HashSet<>(selected).size() != selected.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_CHECKIN_SCHEDULE", "중복 없이 주당 체크인 횟수만큼 요일을 선택해주세요.");
        }
    }

    private void applySettings(Project project, ProjectRequest request, OffsetDateTime now) {
        project.setTitle(request.title());
        project.setClassName(request.className());
        project.setDeadline(request.deadline());
        // PostgreSQL TIME(6) stores microseconds.
        project.setCheckinTime(request.checkinTime().withNano(request.checkinTime().getNano() / 1000 * 1000));
        project.setCheckinFrequency(request.checkinFrequency());
        project.setUpdatedAt(now);
    }
}
