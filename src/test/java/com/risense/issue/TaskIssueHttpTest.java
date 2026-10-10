package com.risense.issue;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.risense.api.error.*;
import com.risense.config.*;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

class TaskIssueHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TaskIssueService service;
    private final String base = "/api/projects/10/tasks/20/issue";

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({TaskIssueController.class, SecurityConfig.class, JwtConfig.class, WebConfig.class, ApiExceptionHandler.class})
    static class TestConfig { @Bean TaskIssueService service() { return mock(TaskIssueService.class); } }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.jwt.secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "app.jwt.issuer", "risense-server", "app.cors.allowed-origins", "http://localhost:5173")));
        context.register(TestConfig.class); context.refresh(); service = context.getBean(TaskIssueService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { if (context != null) context.close(); }

    @Test void issueRequiresLogin() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(patch(base + "/resolve").contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":0}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void missingAndNegativeRevisionAreRejected() throws Exception {
        for (var body : java.util.List.of("{}", "{\"expectedRevision\":-1}")) {
            mvc.perform(patch(base + "/resolve").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }
    @Test void versionConflictUsesExistingErrorEnvelope() throws Exception {
        when(service.resolve(10L, 20L, 7L, 1L)).thenThrow(new ApiException(HttpStatus.CONFLICT, "ISSUE_VERSION_CONFLICT", "다시 조회"));
        mvc.perform(patch(base + "/resolve").with(jwt().jwt(j -> j.subject("7")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"expectedRevision\":1}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ISSUE_VERSION_CONFLICT"));
    }
}
