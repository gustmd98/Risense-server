package com.risense.taskapi;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

public record AssigneeRequest(
        @NotNull @Schema(description = "최종 담당자의 프로젝트 멤버 ID 목록. 빈 배열은 전체 해제. userId가 아님")
        List<@NotNull @Positive Long> memberIds) {}
