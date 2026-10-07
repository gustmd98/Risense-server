package com.risense.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.risense.api.error.ApiException;
import com.risense.domain.user.UserRepository;
import java.sql.SQLException;
import java.time.Clock;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AuthRaceTest {
    @Test
    void concurrentUniqueViolationIsReportedAsDuplicateEmail() {
        var users = mock(UserRepository.class);
        var tokenService = mock(JwtTokenService.class);
        var service = new AuthService(users, new BCryptPasswordEncoder(4), tokenService, Clock.systemUTC());
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("unique violation",
                new ConstraintViolationException("duplicate", new SQLException("duplicate", "23505"),
                        "uk_users_email_normalized")));
        assertThatThrownBy(() -> service.register(new AuthRequests.Register("test@example.com", "password123", "테스터")))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        org.assertj.core.api.Assertions.assertThat(error.getCode()).isEqualTo("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void unrelatedDatabaseFailureIsNotMisreportedAsDuplicate() {
        var users = mock(UserRepository.class);
        var service = new AuthService(users, new BCryptPasswordEncoder(4), mock(JwtTokenService.class), Clock.systemUTC());
        var failure = new DataIntegrityViolationException("unrelated integrity failure");
        when(users.saveAndFlush(any())).thenThrow(failure);
        assertThatThrownBy(() -> service.register(new AuthRequests.Register("test@example.com", "password123", "테스터")))
                .isSameAs(failure);
    }
}
