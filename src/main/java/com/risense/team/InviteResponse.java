package com.risense.team;

import com.risense.domain.invite.InviteLink;
import java.time.OffsetDateTime;

public record InviteResponse(Long id, Long projectId, String token, OffsetDateTime expiresAt, OffsetDateTime createdAt) {
    static InviteResponse from(InviteLink link) {
        return new InviteResponse(link.getId(), link.getProject().getId(), link.getToken(), link.getExpiresAt(), link.getCreatedAt());
    }
    @Override public String toString() { return "InviteResponse[token redacted]"; }
}
