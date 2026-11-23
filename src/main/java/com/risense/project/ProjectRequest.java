package com.risense.project;

import com.risense.domain.project.CheckinDay;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ProjectRequest(
        @NotBlank @Size(max = 100) @Schema(example = "팀플 프로젝트") String title,
        @Size(max = 100) @Schema(example = "소프트웨어공학") String className,
        @NotNull @Schema(example = "2026-12-20") LocalDate deadline,
        @NotNull @Schema(type = "string", example = "23:00:00") LocalTime checkinTime,
        @NotNull @Min(1) @Max(7) @Schema(example = "2") Integer checkinFrequency,
        @NotNull @Size(min = 1, max = 7) @Schema(description = "중복 없이 checkinFrequency 개의 요일")
        List<@NotNull CheckinDay> checkinDays,
        @Pattern(regexp = "presentation|report|development|design|research|other")
        @Schema(description = "생략 시 생성은 other, 수정은 기존 유형 유지", example = "development") String projectType) {
    public ProjectRequest(String title, String className, LocalDate deadline, LocalTime checkinTime,
            Integer checkinFrequency, List<CheckinDay> checkinDays) {
        this(title, className, deadline, checkinTime, checkinFrequency, checkinDays, null);
    }
    public ProjectRequest {
        title = title == null ? null : title.strip();
        className = className == null ? null : className.strip();
    }
}
