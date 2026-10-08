package com.risense.domain.task;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskRepository extends JpaRepository<Task, Long> {
    Optional<Task> findByIdAndProject_Id(long taskId, long projectId);
    List<Task> findByProject_IdOrderBySortOrderAscIdAsc(long projectId);
    @Query("select coalesce(max(t.sortOrder), -1) from Task t where t.project.id = :projectId")
    int maxSortOrder(@Param("projectId") long projectId);
}
