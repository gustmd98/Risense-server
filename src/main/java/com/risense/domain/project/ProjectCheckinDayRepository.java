package com.risense.domain.project;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCheckinDayRepository extends JpaRepository<ProjectCheckinDay, ProjectCheckinDayId> {
    List<ProjectCheckinDay> findById_ProjectId(long projectId);
    List<ProjectCheckinDay> findById_ProjectIdIn(Collection<Long> projectIds);
}
