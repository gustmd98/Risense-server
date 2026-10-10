package com.risense.checkin;

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

class CheckinHttpTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private CheckinService service;
    private final String base = "/api/projects/10/checkins";

    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({CheckinController.class, SecurityConfig.class, JwtConfig.class, WebConfig.class, ApiExceptionHandler.class})
    static class TestConfig { @Bean CheckinService service() { return mock(CheckinService.class); } }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "app.jwt.secret", "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
                "app.jwt.issuer", "risense-server", "app.cors.allowed-origins", "http://localhost:5173")));
        context.register(TestConfig.class); context.refresh(); service = context.getBean(CheckinService.class);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { if (context != null) context.close(); }

    @Test void allSubmissionMethodsRequireLogin() throws Exception {
        mvc.perform(get(base+"/current")).andExpect(status().isUnauthorized());
        mvc.perform(get(base+"/30/submission")).andExpect(status().isUnauthorized());
        mvc.perform(post(base+"/30/submission").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(put(base+"/30/submission").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
    @Test void missingFieldsAndEmptyTasksAreRejected() throws Exception {
        for(var body:java.util.List.of("{}","{\"requestKey\":\"6bfb1793-e84d-47de-98b0-c8226313c233\",\"expectedRevision\":0,\"tasks\":[]}"))
            mvc.perform(post(base+"/30/submission").with(jwt().jwt(j->j.subject("7")))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }
}
