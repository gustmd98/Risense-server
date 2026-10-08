package com.risense.domain.task;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TaskAssigneeRepository extends JpaRepository<TaskAssignee, TaskAssigneeId> {
    @Query("select a from TaskAssignee a join fetch a.member m join fetch m.user where a.task.id = :taskId order by m.id")
    List<TaskAssignee> findForTask(@Param("taskId") long taskId);
    @Query("select a from TaskAssignee a join fetch a.member m join fetch m.user where a.task.id in :taskIds order by m.id")
    List<TaskAssignee> findForTasks(@Param("taskIds") Collection<Long> taskIds);
    @Query("select a from TaskAssignee a join fetch a.task where a.member.id = :memberId")
    List<TaskAssignee> findForMember(@Param("memberId") long memberId);
}
