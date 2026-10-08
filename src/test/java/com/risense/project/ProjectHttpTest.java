package com.risense.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.risense.api.error.*;
import com.risense.config.*;
import java.util.List;
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

class ProjectHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private ProjectService service;

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({ProjectController.class, SecurityConfig.class, JwtConfig.class, WebConfig.class, ApiExceptionHandler.class})
    static class TestConfig {
        @Bean ProjectService projects() { return mock(ProjectService.class); }
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
        service = context.getBean(ProjectService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { if (context != null) context.close(); }

    @Test
    void unauthenticatedProjectCallsReturn401BeforeService() throws Exception {
        mvc.perform(get("/api/projects")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/projects/10/close")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void creationUsesTokenSubjectAndValidatesAndNormalizesJson() throws Exception {
        mvc.perform(post("/api/projects").with(jwt().jwt(token -> token.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isCreated());
        var request = ArgumentCaptor.forClass(ProjectRequest.class);
        verify(service).create(eq(7L), request.capture());
        assertThat(request.getValue().title()).isEqualTo("프로젝트");
        assertThat(request.getValue().checkinDays()).hasSize(2);
    }

    @Test
    void missingFieldsAndInvalidEnumNeverReachService() throws Exception {
        mvc.perform(post("/api/projects").with(jwt().jwt(token -> token.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.fieldErrors.title").exists());
        mvc.perform(post("/api/projects").with(jwt().jwt(token -> token.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(validRequest().replace("MON", "INVALID")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        verifyNoInteractions(service);
    }

    @Test
    void emptyListReturnsJsonArray() throws Exception {
        when(service.list(7L)).thenReturn(List.of());
        mvc.perform(get("/api/projects").with(jwt().jwt(token -> token.subject("7"))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test
    void closedProjectErrorKeepsConflictCode() throws Exception {
        when(service.update(eq(10L), eq(7L), any())).thenThrow(new ApiException(HttpStatus.CONFLICT,
                "PROJECT_NOT_WRITABLE", "종료 프로젝트"));
        mvc.perform(put("/api/projects/10").with(jwt().jwt(token -> token.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(validRequest()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_NOT_WRITABLE"));
    }

    private String validRequest() {
        return """
                {"title":" 프로젝트 ","className":"수업","deadline":"2026-12-20",
                 "checkinTime":"23:00:00","checkinFrequency":2,"checkinDays":["MON","WED"]}
                """;
    }
}
