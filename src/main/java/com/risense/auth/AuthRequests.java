package com.risense.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public final class AuthRequests {
    private AuthRequests() {}

    static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    public record Register(
            @Schema(example = "student@example.com", description = "앞뒤 공백 제거 및 소문자 정규화") @NotBlank @Email @Size(max = 255) String email,
            @Schema(format = "password", example = "example-password-123", description = "8~72자, UTF-8 72바이트 이하") @NotBlank @Size(min = 8, max = 72) String password,
            @Schema(example = "서인이", description = "공백 제거 후 1~50자") @NotBlank @Size(max = 50) String nickname) {
        public Register {
            email = normalizeEmail(email);
            nickname = nickname == null ? null : nickname.strip();
        }
        @Override public String toString() { return "Register[credentials redacted]"; }
    }

    public record Login(
            @Schema(example = "student@example.com", description = "앞뒤 공백 제거 및 소문자 정규화") @NotBlank @Email @Size(max = 255) String email,
            @Schema(format = "password", example = "example-password-123", description = "UTF-8 72바이트 이하") @NotBlank @Size(max = 72) String password) {
        public Login { email = normalizeEmail(email); }
        @Override public String toString() { return "Login[credentials redacted]"; }
    }
}
