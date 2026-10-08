package com.risense.team;

import java.time.OffsetDateTime;

/** Public token validation exposes only the information needed for the join screen. */
public record InvitePreview(Long projectId, String projectTitle, OffsetDateTime expiresAt) {}
