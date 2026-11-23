package com.risense.taskapi;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;

public record TaskTemplateRequest(
        @NotNull @Schema(example = "PRESENTATION") TaskTemplate template,
        @Schema(description = "기존 형식의 공통 마감일. items와 함께 지정할 수 없음") LocalDate dueDate,
        @Size(min = 1, max = 50) List<@NotNull @Valid Selection> items) {
    public TaskTemplateRequest(TaskTemplate template, LocalDate dueDate) { this(template, dueDate, null); }
    public record Selection(@NotBlank String itemId, LocalDate startDate, LocalDate dueDate,
            @Size(max = 50) List<@NotBlank String> prerequisiteItemIds) {}
}
