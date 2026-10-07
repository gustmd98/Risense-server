package com.risense.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.risense.api.HealthController;
import com.risense.api.error.ApiExceptionHandler;
import com.risense.config.JwtConfig;
import com.risense.config.SecurityConfig;
import com.risense.config.WebConfig;
import com.risense.domain.user.User;
import com.risense.domain.user.UserRepository;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** Real MVC validation, password encoder, JWT signer/decoder and security filters; no DB/port needed. */
class AuthHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private UserRepository users;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({AuthController.class, AuthService.class, JwtTokenService.class, JwtConfig.class,
            SecurityConfig.class, WebConfig.class, HealthController.class, ApiExceptionHandler.class})
    static class TestConfig {
        @Bean UserRepository users() { return mock(UserRepository.class); }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.jwt.secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", // test fixture only
                "app.jwt.issuer", "risense-server", "app.jwt.access-token-seconds", "3600",
                "app.cors.allowed-origins", "http://localhost:5173")));
        context.register(TestConfig.class);
        context.refresh();
        users = context.getBean(UserRepository.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void close() { if (context != null) context.close(); }

    @Test
    void registrationHashesPasswordAndReturnsOnlyPublicUserFields() throws Exception {
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User saved = call.getArgument(0); saved.setId(1L); return saved;
        });
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\" TEST@Example.com \",\"password\":\"password123\",\"nickname\":\" 테스터 \"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.nickname").value("테스터"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist()).andExpect(jsonPath("$.password").doesNotExist());
        var captured = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).saveAndFlush(captured.capture());
        assertThat(captured.getValue().getPasswordHash()).isNotEqualTo("password123");
        assertThat(context.getBean(PasswordEncoder.class).matches("password123", captured.getValue().getPasswordHash())).isTrue();
        assertThat(captured.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        when(users.findByNormalizedEmail("test@example.com")).thenReturn(Optional.of(account()));
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(validRegistration()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
        verify(users, never()).saveAndFlush(any());
    }

    @Test
    void invalidRegistrationIsRejectedBeforeStorage() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"invalid\",\"password\":\"short\",\"nickname\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.email").exists()).andExpect(jsonPath("$.fieldErrors.password").exists());
        verifyNoInteractions(users);
    }

    @Test
    void oversizedMultibytePasswordIsRejectedWithoutBcryptTruncation() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"test@example.com\",\"password\":\"" + "가".repeat(25) + "\",\"nickname\":\"테스터\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_PASSWORD"));
        verifyNoInteractions(users);
    }

    @Test
    void loginIssuesUsableTokenAndMeReturnsUser() throws Exception {
        User user = account();
        when(users.findByNormalizedEmail("test@example.com")).thenReturn(Optional.of(user));
        when(users.findById(1L)).thenReturn(Optional.of(user));
        String response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"TEST@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600)).andReturn().getResponse().getContentAsString();
        var matcher = Pattern.compile("\"accessToken\"\\s*:\\s*\"([^\"]+)\"").matcher(response);
        assertThat(matcher.find()).isTrue();
        String token = matcher.group(1);
        assertThat(context.getBean(JwtDecoder.class).decode(token).getSubject()).isEqualTo("1");
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void unknownEmailAndWrongPasswordReturnSameError() throws Exception {
        String request = "{\"email\":\"test@example.com\",\"password\":\"wrong-password\"}";
        String unknown = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        when(users.findByNormalizedEmail("test@example.com")).thenReturn(Optional.of(account()));
        String wrong = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        assertThat(wrong).isEqualTo(unknown).contains("INVALID_CREDENTIALS");
    }

    @Test
    void protectedEndpointsRequireValidBearerToken() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        String valid = context.getBean(JwtTokenService.class).issue(1);
        int signature = valid.lastIndexOf('.') + 1;
        String altered = valid.substring(0, signature)
                + (valid.charAt(signature) == 'A' ? "B" : "A") + valid.substring(signature + 1);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + altered))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void expiredWrongIssuerAndInvalidSubjectTokensAreRejected() throws Exception {
        for (String token : new String[]{signed("1", "risense-server", Instant.now().minusSeconds(60)),
                signed("1", "wrong-issuer", Instant.now().plusSeconds(600)),
                signed("not-an-id", "risense-server", Instant.now().plusSeconds(600))}) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(users);
    }

    @Test
    void tokenForDeletedUserIsUnauthorized() throws Exception {
        String token = context.getBean(JwtTokenService.class).issue(999);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void corsAllowsConfiguredFrontendPreflight() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void malformedJsonIsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private User account() {
        User user = User.register("test@example.com", context.getBean(PasswordEncoder.class).encode("password123"),
                "테스터", OffsetDateTime.now());
        user.setId(1L);
        return user;
    }

    private String signed(String subject, String issuer, Instant expiry) {
        var claims = JwtClaimsSet.builder().issuer(issuer).subject(subject)
                .issuedAt(Instant.now().minusSeconds(120)).expiresAt(expiry).claim("token_use", "access").build();
        return context.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private String validRegistration() {
        return "{\"email\":\"test@example.com\",\"password\":\"password123\",\"nickname\":\"테스터\"}";
    }
}
