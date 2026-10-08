package com.risense.domain.task;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SubTaskRepository extends JpaRepository<SubTask, Long> {
    Optional<SubTask> findByIdAndTask_Id(long subTaskId, long taskId);
    @Query("select s from SubTask s left join fetch s.assigneeMember m left join fetch m.user where s.task.id = :taskId order by s.sortOrder, s.id")
    List<SubTask> findForTask(@Param("taskId") long taskId);
    @Query("select coalesce(max(s.sortOrder), -1) from SubTask s where s.task.id = :taskId")
    int maxSortOrder(@Param("taskId") long taskId);
    @Query("select s from SubTask s join fetch s.task where s.assigneeMember.id = :memberId")
    List<SubTask> findForMember(@Param("memberId") long memberId);
}
