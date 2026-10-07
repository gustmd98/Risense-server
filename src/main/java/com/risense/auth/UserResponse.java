package com.risense.auth;

import com.risense.domain.user.User;
import java.time.OffsetDateTime;

public record UserResponse(Long id, String email, String nickname, OffsetDateTime createdAt) {
    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getNickname(), user.getCreatedAt());
    }
}
