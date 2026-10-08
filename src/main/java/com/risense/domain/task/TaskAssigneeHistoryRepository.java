package com.risense.domain.task;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAssigneeHistoryRepository extends JpaRepository<TaskAssigneeHistory, Long> {
    @Query("select h from TaskAssigneeHistory h join fetch h.member m join fetch m.user "
            + "join fetch h.changedBy c join fetch c.user where h.task.id = :taskId order by h.changedAt desc, h.id desc")
    List<TaskAssigneeHistory> findForTask(@Param("taskId") long taskId);
}
