package com.risense.taskapi;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record TaskTemplateRequest(
        @NotNull @Schema(description = "생성할 템플릿", example = "PRESENTATION") TaskTemplate template,
        @Schema(description = "생성되는 작업의 공통 마감일. 생략하면 마감일 없이 생성", example = "2026-12-15") LocalDate dueDate) {}
