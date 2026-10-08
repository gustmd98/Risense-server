package com.risense.taskapi;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.util.List;

public final class RelationRequests {
    private RelationRequests() {}
    public record Prerequisites(@NotNull @Schema(description = "최종 선행 작업 ID 목록. 빈 배열은 전체 해제")
            List<@NotNull @Positive Long> prerequisiteTaskIds) {}
    public record SubTaskSettings(@NotBlank @Size(max = 100) @Schema(example = "자료 조사") String title,
            @Positive @Schema(description = "프로젝트 멤버 ID. null이면 미배정") Long assigneeMemberId,
            @Min(0) @Schema(description = "생성 시 null이면 끝에 추가, 수정 시 null이면 기존 순서 유지") Integer sortOrder) {
        public SubTaskSettings { title = title == null ? null : title.strip(); }
    }
    public record Completion(@NotNull @Schema(example = "true") Boolean completed) {}
    public record Artifact(@Size(max = 100) @Schema(example = "발표 슬라이드") String title,
            @NotBlank @Size(max = 500) @Schema(example = "https://docs.google.com/presentation/d/example") String url) {
        public Artifact {
            title = title == null || title.isBlank() ? null : title.strip();
            url = url == null ? null : url.strip();
        }
    }
}
