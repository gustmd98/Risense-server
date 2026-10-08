package com.risense.domain.task;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskPrerequisiteRepository extends JpaRepository<TaskPrerequisite, TaskPrerequisiteId> {
    List<TaskPrerequisite> findByTask_Project_Id(long projectId);
    @Query("select p from TaskPrerequisite p join fetch p.prerequisiteTask where p.task.id = :taskId order by p.id.prerequisiteTaskId")
    List<TaskPrerequisite> findForTask(@Param("taskId") long taskId);
}
