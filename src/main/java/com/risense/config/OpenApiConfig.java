package com.risense.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI risenseOpenApi() {
        return new OpenAPI().info(new Info().title("Risense API").version("v1")
                .description("회원가입 후 로그인하여 accessToken을 발급받습니다. Authorize에는 토큰 값만 입력합니다. 토큰 만료 시 다시 로그인합니다."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean
    GroupedOpenApi authApi() {
        return GroupedOpenApi.builder().group("auth").pathsToMatch("/api/auth/**").build();
    }

    @Bean
    GroupedOpenApi projectApi() {
        return GroupedOpenApi.builder().group("projects").pathsToMatch("/api/projects/**").build();
    }
}
