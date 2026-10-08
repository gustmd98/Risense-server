package com.risense.domain.task;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskArtifactRepository extends JpaRepository<TaskArtifact, Long> {
    Optional<TaskArtifact> findByIdAndTask_Id(long artifactId, long taskId);
    @Query("select a from TaskArtifact a join fetch a.createdBy m join fetch m.user where a.task.id = :taskId order by a.createdAt desc, a.id desc")
    List<TaskArtifact> findForTask(@Param("taskId") long taskId);
}
