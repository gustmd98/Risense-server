package com.risense.auth;

import java.time.Clock;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {
    private final JwtEncoder encoder;
    private final Clock clock;
    private final String issuer;
    private final long lifetime;

    public JwtTokenService(JwtEncoder encoder, Clock clock,
            @Value("${app.jwt.issuer}") String issuer,
            @Value("${app.jwt.access-token-seconds}") long lifetime) {
        if (lifetime < 1 || lifetime > 86400) throw new IllegalArgumentException("Invalid access token lifetime");
        this.encoder = encoder;
        this.clock = clock;
        this.issuer = issuer;
        this.lifetime = lifetime;
    }

    public String issue(long userId) {
        Instant now = clock.instant();
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(Long.toString(userId))
                .issuedAt(now).expiresAt(now.plusSeconds(lifetime)).claim("token_use", "access").build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims)).getTokenValue();
    }

    public long getLifetime() { return lifetime; }
}
