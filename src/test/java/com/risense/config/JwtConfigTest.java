package com.risense.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtConfigTest {
    @Test
    void malformedOrShortSecretsAreRejectedWithoutEchoingTheSecret() {
        var config = new JwtConfig();
        assertThatThrownBy(() -> config.jwtSecretKey("not base64!"))
                .isInstanceOf(IllegalStateException.class).hasMessage("JWT_SECRET must be Base64 encoded");
        assertThatThrownBy(() -> config.jwtSecretKey(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT_SECRET must contain at least 32 random bytes");
    }

    @Test
    void validHs256SecretIsAccepted() {
        var config = new JwtConfig();
        assertThat(config.jwtSecretKey(Base64.getEncoder().encodeToString(new byte[32])).getAlgorithm())
                .isEqualTo("HmacSHA256");
    }
}
