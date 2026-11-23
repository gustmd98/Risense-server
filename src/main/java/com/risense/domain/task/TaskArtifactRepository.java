package com.risense.domain.task;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskArtifactRepository extends JpaRepository<TaskArtifact, Long> {
    @Query("select distinct a.task.id from TaskArtifact a where a.task.project.id = :projectId")
    List<Long> findTaskIdsWithArtifacts(@Param("projectId") long projectId);
    boolean existsByTask_Id(long taskId);
    Optional<TaskArtifact> findByIdAndTask_Id(long artifactId, long taskId);
    @Query("select a from TaskArtifact a join fetch a.createdBy m join fetch m.user where a.task.id = :taskId order by a.createdAt desc, a.id desc")
    List<TaskArtifact> findForTask(@Param("taskId") long taskId);
}
