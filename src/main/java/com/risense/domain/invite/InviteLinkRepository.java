package com.risense.domain.invite;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InviteLinkRepository extends JpaRepository<InviteLink, Long> {
    Optional<InviteLink> findByToken(String token);
    Optional<InviteLink> findByIdAndProject_Id(long id, long projectId);
    List<InviteLink> findByProject_IdAndIsActiveTrue(long projectId);
    List<InviteLink> findByCreatedBy_IdAndIsActiveTrue(long memberId);

    // Scalar lookup avoids caching a stale link before waiting for the project write lock.
    @Query("select i.project.id from InviteLink i where i.token = :token")
    Optional<Long> findProjectIdByToken(@Param("token") String token);
}
