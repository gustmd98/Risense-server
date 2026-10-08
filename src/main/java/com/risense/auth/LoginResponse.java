package com.risense.auth;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginResponse(@Schema(description = "서명된 JWT. Authorization 헤더로 전달") String accessToken, @Schema(example = "Bearer") String tokenType, @Schema(example = "3600", description = "토큰 유효 기간(초)") long expiresIn, UserResponse user) {
    @Override public String toString() { return "LoginResponse[token redacted]"; }
}
