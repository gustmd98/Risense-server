package com.risense.taskapi;

import com.risense.domain.task.TaskSize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;

public record TaskRequest(
        @NotBlank @Size(max = 100) @Schema(example = "발표 자료 작성") String title,
        @NotNull @Schema(example = "M") TaskSize size,
        @Schema(example = "2026-12-15", description = "null이면 마감일 없음") LocalDate dueDate,
        @Min(0) @Schema(description = "생성 시 생략하면 목록 끝, 수정 시 생략하면 기존 순서 유지") Integer sortOrder) {
    public TaskRequest { title = title == null ? null : title.strip(); }
}
