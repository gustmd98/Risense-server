package com.risense.auth;

import com.risense.api.error.ApiException;
import com.risense.domain.user.User;
import com.risense.domain.user.UserRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final Clock clock;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtTokenService tokens, Clock clock) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.clock = clock;
        this.dummyHash = passwords.encode("dummy-login-comparison");
    }

    @Transactional
    public UserResponse register(AuthRequests.Register request) {
        requirePasswordLength(request.password());
        if (users.findByNormalizedEmail(request.email()).isPresent()) throw duplicateEmail();
        User user = User.register(request.email(), passwords.encode(request.password()),
                request.nickname(), OffsetDateTime.now(clock));
        try {
            return UserResponse.from(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException error) {
            // The unique index also handles two concurrent signups after the initial lookup.
            for (Throwable cause = error; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && ("uk_users_email".equals(violation.getConstraintName())
                            || "uk_users_email_normalized".equals(violation.getConstraintName()))) {
                    throw duplicateEmail();
                }
            }
            throw error;
        }
    }

    @Transactional(readOnly = true)
    public LoginResponse login(AuthRequests.Login request) {
        requirePasswordLength(request.password());
        var user = users.findByNormalizedEmail(request.email());
        boolean matches = passwords.matches(request.password(), user.map(User::getPasswordHash).orElse(dummyHash));
        if (user.isEmpty() || !matches) throw invalidCredentials();
        var found = user.orElseThrow();
        return new LoginResponse(tokens.issue(found.getId()), "Bearer", tokens.getLifetime(), UserResponse.from(found));
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(long userId) {
        return users.findById(userId).map(UserResponse::from).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "다시 로그인해주세요."));
    }

    private void requirePasswordLength(String password) {
        // BCrypt limits bytes, not Java characters; never silently truncate a multibyte password.
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PASSWORD", "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.");
        }
    }

    private ApiException duplicateEmail() {
        return new ApiException(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 사용 중인 이메일입니다.");
    }

    private ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "이메일 또는 비밀번호를 확인해주세요.");
    }
}
