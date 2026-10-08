package com.risense.domain.member;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {
    Optional<ProjectMember> findByProject_IdAndUser_Id(long projectId, long userId);

    @Query("select m from ProjectMember m join fetch m.project where m.user.id = :userId "
            + "and m.joinStatus = :status order by m.project.createdAt desc, m.project.id desc")
    List<ProjectMember> findMemberships(@Param("userId") long userId, @Param("status") MemberStatus status);
}
