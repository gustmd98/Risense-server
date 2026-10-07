package com.risense.auth;

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
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @NotBlank @Size(max = 50) String nickname) {
        public Register {
            email = normalizeEmail(email);
            nickname = nickname == null ? null : nickname.strip();
        }
        @Override public String toString() { return "Register[credentials redacted]"; }
    }

    public record Login(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(max = 72) String password) {
        public Login { email = normalizeEmail(email); }
        @Override public String toString() { return "Login[credentials redacted]"; }
    }
}
