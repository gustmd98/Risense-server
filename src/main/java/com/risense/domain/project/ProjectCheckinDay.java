package com.risense.domain.project;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_checkin_days")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCheckinDay {

    public static ProjectCheckinDay of(Project project, CheckinDay day) {
        ProjectCheckinDay schedule = new ProjectCheckinDay();
        schedule.project = project;
        schedule.id = new ProjectCheckinDayId(project.getId(), day);
        return schedule;
    }

    @EmbeddedId
    private ProjectCheckinDayId id;

    @MapsId("projectId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, foreignKey = @ForeignKey(name = "fk_checkin_days_project"))
    private Project project;
}
