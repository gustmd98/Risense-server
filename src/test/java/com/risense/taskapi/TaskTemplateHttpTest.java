package com.risense.taskapi;

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

class TaskTemplateHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TaskTemplateService service;
    private final String base = "/api/projects/10/tasks";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({TaskTemplateController.class, TaskController.class, SecurityConfig.class, JwtConfig.class, WebConfig.class, ApiExceptionHandler.class})
    static class TestConfig {
        @Bean TaskTemplateService service() { return mock(TaskTemplateService.class); }
        @Bean TaskService tasks() { return mock(TaskService.class); }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.jwt.secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "app.jwt.issuer", "risense-server", "app.cors.allowed-origins", "http://localhost:5173")));
        context.register(TestConfig.class); context.refresh();
        service = context.getBean(TaskTemplateService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void close() { if (context != null) context.close(); }


    @Test void bothRoutesRequireAuthentication() throws Exception {
        mvc.perform(get(base + "/templates")).andExpect(status().isUnauthorized());
        mvc.perform(post(base + "/from-template").contentType(MediaType.APPLICATION_JSON)
                .content("{\"template\":\"PRESENTATION\"}")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void missingUnknownTemplateAndBadDateReturn400() throws Exception {
        for (String body : List.of("{}", "{\"template\":null}", "{\"template\":\"UNKNOWN\"}",
                "{\"template\":\"REPORT\",\"dueDate\":\"invalid\"}")) {
            mvc.perform(post(base + "/from-template").with(jwt().jwt(j -> j.subject("7")))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(service);
    }

    @Test void previewRouteDoesNotMatchTaskIdAndReturnsCatalog() throws Exception {
        when(service.list(10L, 7L)).thenReturn(java.util.Arrays.stream(TaskTemplate.values()).map(TaskTemplate::summary).toList());
        mvc.perform(get(base + "/templates").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].template").value("PRESENTATION"))
                .andExpect(jsonPath("$[0].tasks.length()").value(5))
                .andExpect(jsonPath("$[0].tasks[0].size").value("S"));
        verify(service).list(10L, 7L);
    }

    @Test void creationUsesJwtAndAcceptsOptionalDeadline() throws Exception {
        when(service.create(anyLong(), anyLong(), any())).thenReturn(List.of());
        mvc.perform(post(base + "/from-template").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"template\":\"REPORT\"}"))
                .andExpect(status().isCreated()).andExpect(content().json("[]"));
        verify(service).create(10L, 7L, new TaskTemplateRequest(TaskTemplate.REPORT, null));
        mvc.perform(post(base + "/from-template").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"template\":\"RESEARCH\",\"dueDate\":\"2026-12-15\"}"))
                .andExpect(status().isCreated());
        verify(service).create(10L, 7L, new TaskTemplateRequest(TaskTemplate.RESEARCH, java.time.LocalDate.of(2026, 12, 15)));
    }

    @Test void closedProjectReturnsConflict() throws Exception {
        when(service.create(anyLong(), anyLong(), any())).thenThrow(new ApiException(HttpStatus.CONFLICT, "PROJECT_NOT_WRITABLE", "종료 프로젝트"));
        mvc.perform(post(base + "/from-template").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"template\":\"DESIGN\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_NOT_WRITABLE"));
    }
}
