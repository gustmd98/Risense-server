package com.risense.team;

import com.risense.domain.member.MemberRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;

public final class TeamRequests {
    private TeamRequests() {}

    public record Invite(@Min(1) @Max(720) @Schema(example = "168", description = "만료까지 시간. 기본 168시간, 최대 720시간") Integer expiresInHours) {
        public Invite { expiresInHours = expiresInHours == null ? 168 : expiresInHours; }
    }

    public record Role(@NotNull @Schema(description = "LEADER / CO_LEADER / MEMBER") MemberRole role) {}
}
