package com.risense.taskapi;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record TaskScheduleRequest(
        @JsonProperty(required = true) @Schema(description = "시작일. 제거는 명시적 null") LocalDate startDate,
        @JsonProperty(required = true) @Schema(description = "마감일. 제거는 명시적 null") LocalDate dueDate,
        @NotNull @PositiveOrZero @Schema(description = "최신 작업 응답의 revision. 불일치 시 409") Long expectedRevision) {}
