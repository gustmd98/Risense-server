package com.risense.project;

import com.risense.domain.member.MemberRole;
import com.risense.domain.project.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;

public record ProjectResponse(Long id, String title, String className, LocalDate deadline,
        LocalTime checkinTime, Integer checkinFrequency, List<CheckinDay> checkinDays,
        ProjectStatus status, MemberRole myRole, OffsetDateTime closedAt,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    static ProjectResponse from(Project project, MemberRole role, List<CheckinDay> days) {
        return new ProjectResponse(project.getId(), project.getTitle(), project.getClassName(),
                project.getDeadline(), project.getCheckinTime(), project.getCheckinFrequency(),
                days.stream().sorted().toList(), project.getStatus(), role,
                project.getClosedAt(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
