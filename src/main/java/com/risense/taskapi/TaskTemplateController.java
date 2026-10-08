package com.risense.taskapi;

import com.risense.api.error.ApiError;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks")
@Tag(name = "작업 템플릿")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGER_REQUIRED", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "PROJECT_NOT_WRITABLE / TASK_ORDER_LIMIT", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TaskTemplateController {
    private final TaskTemplateService templates;
    public TaskTemplateController(TaskTemplateService templates) { this.templates = templates; }

    @GetMapping("/templates")
    @Operation(summary = "작업 템플릿 목록", description = "승인된 팀원 전용. 다섯 가지 템플릿의 작업 제목과 크기를 반환합니다.")
    public List<TaskTemplate.Summary> list(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return templates.list(projectId, Long.parseLong(jwt.getSubject()));
    }

    @PostMapping("/from-template")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "템플릿 기반 작업 일괄 생성", description = "팀장·공동 팀장 전용. 기존 작업 뒤에 TODO/진행률 0/담당자 없는 작업을 생성합니다. 한 요청의 작업은 모두 함께 저장됩니다. 반복 호출하면 새 작업이 추가됩니다.")
    public List<TaskResponse> create(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TaskTemplateRequest request) {
        return templates.create(projectId, Long.parseLong(jwt.getSubject()), request);
    }
}
