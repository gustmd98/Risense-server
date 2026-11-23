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

class BoardHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TaskService service;
    private final String base = "/api/projects/10/tasks";

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({TaskController.class, TaskDependencyController.class, com.risense.report.ProjectReportController.class, SecurityConfig.class, JwtConfig.class, WebConfig.class, ApiExceptionHandler.class})
    static class TestConfig {
        @Bean TaskService service() { return mock(TaskService.class); }
        @Bean TaskRelationService relations() { return mock(TaskRelationService.class); }
        @Bean com.risense.report.ProjectReportService reports() { return mock(com.risense.report.ProjectReportService.class); }
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

    @Test void datePatchRequiresBothKeysAndExpectedRevision() throws Exception {
        for(String body:List.of("{}","{\"expectedRevision\":0}","{\"startDate\":null,\"expectedRevision\":0}","{\"startDate\":null,\"dueDate\":null}"))
            mvc.perform(patch(base+"/20/schedule").with(jwt().jwt(j->j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void explicitNullDatesClearAndRevisionIsForwarded() throws Exception {
        mvc.perform(patch(base+"/20/schedule").with(jwt().jwt(j->j.subject("7")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":null,\"dueDate\":null,\"expectedRevision\":4}"))
            .andExpect(status().isOk());
        verify(service).schedule(10L,20L,7L,new TaskScheduleRequest(null,null,4L));
    }
    @Test void staticDependencyPathAndReportPathResolveAndRequireAuthentication() throws Exception {
        mvc.perform(get(base+"/dependencies")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/projects/10/report")).andExpect(status().isUnauthorized());
        var relations=context.getBean(TaskRelationService.class);
        when(relations.dependencies(10L,7L)).thenReturn(List.of(new TaskRelationService.Dependency(20L,19L)));
        mvc.perform(get(base+"/dependencies").with(jwt().jwt(j->j.subject("7"))))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].taskId").value(20))
            .andExpect(jsonPath("$[0].prerequisiteTaskId").value(19));
        mvc.perform(get("/api/projects/10/report").with(jwt().jwt(j->j.subject("7"))))
            .andExpect(status().isOk());
        verify(context.getBean(com.risense.report.ProjectReportService.class)).report(10L,7L,100);
    }
    @Test void staleScheduleReturnsConflict() throws Exception {
        when(service.schedule(anyLong(),anyLong(),anyLong(),any())).thenThrow(new ApiException(HttpStatus.CONFLICT,"TASK_VERSION_CONFLICT","changed"));
        mvc.perform(patch(base+"/20/schedule").with(jwt().jwt(j->j.subject("7")))
            .contentType(MediaType.APPLICATION_JSON).content("{\"startDate\":null,\"dueDate\":null,\"expectedRevision\":0}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_VERSION_CONFLICT"));
    }
}
