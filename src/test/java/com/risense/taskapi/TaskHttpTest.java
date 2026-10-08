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

class TaskHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TaskService service;
    private final String base = "/api/projects/10/tasks";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({TaskController.class, SecurityConfig.class, JwtConfig.class, WebConfig.class, ApiExceptionHandler.class})
    static class TestConfig {
        @Bean TaskService service() { return mock(TaskService.class); }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.jwt.secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "app.jwt.issuer", "risense-server", "app.cors.allowed-origins", "http://localhost:5173")));
        context.register(TestConfig.class); context.refresh();
        service = context.getBean(TaskService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach void close() { if (context != null) context.close(); }

    @Test void unauthenticatedReadsAndWritesAre401() throws Exception {
        mvc.perform(get(base)).andExpect(status().isUnauthorized());
        mvc.perform(post(base).contentType(MediaType.APPLICATION_JSON).content(valid()))
                .andExpect(status().isUnauthorized());
        mvc.perform(put(base + "/20/assignees").contentType(MediaType.APPLICATION_JSON).content("{\"memberIds\":[]}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test void invalidMetadataAndMemberIdsReturn400() throws Exception {
        mvc.perform(post(base).with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \",\"size\":\"M\",\"sortOrder\":-1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.title").exists());
        mvc.perform(put(base + "/20/assignees").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"memberIds\":[null,-1]}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post(base).with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(valid().replace("M", "XXL")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void createAndAssignUsePrincipalAndScopedIds() throws Exception {
        mvc.perform(post(base).with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(valid())).andExpect(status().isCreated());
        verify(service).create(eq(10L), eq(7L), any(TaskRequest.class));
        mvc.perform(put(base + "/20/assignees").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"memberIds\":[3]}"))
                .andExpect(status().isOk());
        verify(service).setAssignees(10L, 20L, 7L, new AssigneeRequest(List.of(3L)));
    }

    @Test void emptyListIsJsonArray() throws Exception {
        when(service.list(10L, 7L)).thenReturn(List.of());
        mvc.perform(get(base).with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
    }

    @Test void cancelledTaskErrorIsConflict() throws Exception {
        when(service.update(eq(10L), eq(20L), eq(7L), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "TASK_CANCELLED", "취소 작업"));
        mvc.perform(put(base + "/20").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(valid())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_CANCELLED"));
    }

    private String valid() { return "{\"title\":\"발표 자료 작성\",\"size\":\"M\",\"dueDate\":null}"; }
}
