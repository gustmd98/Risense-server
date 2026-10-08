package com.risense.taskapi;

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

class TaskRelationHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private TaskRelationService service;
    private final String base = "/api/projects/10/tasks/20";

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({TaskRelationController.class, SecurityConfig.class, JwtConfig.class, WebConfig.class, ApiExceptionHandler.class})
    static class TestConfig { @Bean TaskRelationService service() { return mock(TaskRelationService.class); } }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.jwt.secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "app.jwt.issuer", "risense-server", "app.cors.allowed-origins", "http://localhost:5173")));
        context.register(TestConfig.class); context.refresh(); service = context.getBean(TaskRelationService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { if (context != null) context.close(); }

    @Test void allResourcesRequireLogin() throws Exception {
        mvc.perform(get(base + "/prerequisites")).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/sub-tasks")).andExpect(status().isUnauthorized());
        mvc.perform(get(base + "/artifacts")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void payloadValidationRejectsNullCompletionAndInvalidIds() throws Exception {
        mvc.perform(patch(base + "/sub-tasks/30/completion").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.completed").exists());
        mvc.perform(put(base + "/prerequisites").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"prerequisiteTaskIds\":[null,-1]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post(base + "/artifacts").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"url\":\" \"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
    @Test void creationCompletionAndDeletionUseScopedIdsAndJwt() throws Exception {
        mvc.perform(post(base + "/sub-tasks").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"조사\",\"assigneeMemberId\":3}"))
                .andExpect(status().isCreated());
        verify(service).createChild(10L, 20L, 7L, new RelationRequests.SubTaskSettings("조사", 3L, null));
        mvc.perform(patch(base + "/sub-tasks/30/completion").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"completed\":true}"))
                .andExpect(status().isOk());
        verify(service).completeChild(10L, 20L, 30L, 7L, new RelationRequests.Completion(true));
        mvc.perform(delete(base + "/artifacts/40").with(jwt().jwt(j -> j.subject("7"))))
                .andExpect(status().isNoContent());
        verify(service).deleteArtifact(10L, 20L, 40L, 7L);
    }
    @Test void graphCycleErrorUsesConflictResponse() throws Exception {
        when(service.setPrerequisites(eq(10L), eq(20L), eq(7L), any())).thenThrow(
                new ApiException(HttpStatus.CONFLICT, "TASK_DEPENDENCY_CYCLE", "순환"));
        mvc.perform(put(base + "/prerequisites").with(jwt().jwt(j -> j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content("{\"prerequisiteTaskIds\":[21]}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("TASK_DEPENDENCY_CYCLE"));
    }
}
