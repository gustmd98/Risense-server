package com.risense.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.risense.api.error.*;
import com.risense.config.*;
import com.risense.domain.member.MemberRole;
import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class TeamHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TeamService team;
    private final String token = "a".repeat(64);

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({TeamController.class, InviteController.class, SecurityConfig.class, JwtConfig.class,
            WebConfig.class, ApiExceptionHandler.class})
    static class TestConfig {
        @Bean TeamService team() { return mock(TeamService.class); }
    }

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.jwt.secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "app.jwt.issuer", "risense-server", "app.cors.allowed-origins", "http://localhost:5173")));
        context.register(TestConfig.class);
        context.refresh();
        team = context.getBean(TeamService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void close() { if (context != null) context.close(); }

    @Test
    void previewIsPublicAndContainsNoMembershipOrEmail() throws Exception {
        when(team.preview(token)).thenReturn(new InvitePreview(10L, "프로젝트", OffsetDateTime.parse("2026-12-20T00:00:00Z")));
        mvc.perform(get("/api/invites/" + token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.projectId").value(10)).andExpect(jsonPath("$.projectTitle").value("프로젝트"))
                .andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.members").doesNotExist());
    }

    @Test
    void publicPreviewDoesNotPermitUnauthenticatedJoinOrTeamReads() throws Exception {
        mvc.perform(post("/api/invites/" + token + "/join")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/10/members")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/10/invite-links/current")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/projects/10/invite-links")).andExpect(status().isUnauthorized());
        verifyNoInteractions(team);
    }

    @Test
    void joiningUsesTokenSubject() throws Exception {
        mvc.perform(post("/api/invites/" + token + "/join").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isOk());
        verify(team).join(token, 7L);
    }

    @Test
    void expiryAndRolePayloadsAreValidatedBeforeService() throws Exception {
        mvc.perform(post("/api/projects/10/invite-links").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"expiresInHours\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(patch("/api/projects/10/members/2/role").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.role").exists());
        mvc.perform(patch("/api/projects/10/members/2/role").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(team);
    }

    @Test
    void emptyInviteObjectDefaultsToSevenDays() throws Exception {
        mvc.perform(post("/api/projects/10/invite-links").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
        var request = ArgumentCaptor.forClass(TeamRequests.Invite.class);
        verify(team).issue(eq(10L), eq(7L), request.capture());
        assertThat(request.getValue().expiresInHours()).isEqualTo(168);
    }

    @Test
    void roleAndRevokeRoutesUseMemberIdAndPrincipalAndReturnExpectedStatus() throws Exception {
        mvc.perform(patch("/api/projects/10/members/2/role").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"CO_LEADER\"}"))
                .andExpect(status().isOk());
        verify(team).changeRole(10L, 2L, 7L, MemberRole.CO_LEADER);
        mvc.perform(delete("/api/projects/10/invite-links/3").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isNoContent());
        verify(team).revoke(10L, 3L, 7L);
    }

    @Test
    void expirationAndLastLeaderErrorsKeepTheirCodes() throws Exception {
        when(team.preview(token)).thenThrow(new ApiException(HttpStatus.GONE, "INVITE_UNAVAILABLE", "만료"));
        mvc.perform(get("/api/invites/" + token)).andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("INVITE_UNAVAILABLE"));
        when(team.remove(10L, 2L, 7L)).thenThrow(new ApiException(HttpStatus.CONFLICT, "LAST_LEADER", "마지막 팀장"));
        mvc.perform(delete("/api/projects/10/members/2").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("LAST_LEADER"));
    }
}
