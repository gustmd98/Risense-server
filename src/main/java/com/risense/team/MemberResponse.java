package com.risense.team;

import com.risense.domain.member.*;
import java.time.OffsetDateTime;

public record MemberResponse(Long id, Long userId, String nickname, MemberRole role,
        MemberStatus joinStatus, OffsetDateTime requestedAt, OffsetDateTime joinedAt, OffsetDateTime removedAt) {
    static MemberResponse from(ProjectMember member) {
        return new MemberResponse(member.getId(), member.getUser().getId(), member.getUser().getNickname(),
                member.getRole(), member.getJoinStatus(), member.getRequestedAt(), member.getJoinedAt(), member.getRemovedAt());
    }
}
