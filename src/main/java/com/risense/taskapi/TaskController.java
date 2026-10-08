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
@Tag(name = "작업 관리")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST / INVALID_ASSIGNEES", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGER_REQUIRED", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / TASK_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "PROJECT_NOT_WRITABLE / TASK_CANCELLED / TASK_ORDER_LIMIT", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TaskController {
    private final TaskService tasks;
    public TaskController(TaskService tasks) { this.tasks = tasks; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "작업 생성", description = "팀장·공동 팀장 전용. TODO/진행률 0, 담당자 없이 생성합니다.")
    public TaskResponse create(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TaskRequest request) {
        return tasks.create(projectId, userId(jwt), request);
    }

    @GetMapping
    @Operation(summary = "작업 목록", description = "승인된 팀원 전용. sortOrder, id 순으로 취소된 작업도 포함합니다.")
    public List<TaskResponse> list(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return tasks.list(projectId, userId(jwt));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "작업 상세", description = "승인된 팀원 전용.")
    public TaskResponse detail(@PathVariable("projectId") long projectId, @PathVariable("taskId") long taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return tasks.detail(projectId, taskId, userId(jwt));
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "작업 설정 수정", description = "팀장·공동 팀장 전용. 진행률·상태는 변경하지 않습니다. dueDate null은 마감일 제거, sortOrder null은 기존 순서 유지입니다.")
    public TaskResponse update(@PathVariable("projectId") long projectId, @PathVariable("taskId") long taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TaskRequest request) {
        return tasks.update(projectId, taskId, userId(jwt), request);
    }

    @PostMapping("/{taskId}/cancel")
    @Operation(summary = "작업 취소", description = "팀장·공동 팀장 전용. 행과 이력을 보존하고 CANCELLED로 전환합니다. 반복 요청은 최초 취소 시간을 유지합니다.")
    public TaskResponse cancel(@PathVariable("projectId") long projectId, @PathVariable("taskId") long taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return tasks.cancel(projectId, taskId, userId(jwt));
    }

    @PutMapping("/{taskId}/assignees")
    @Operation(summary = "작업 담당자 변경", description = "팀장·공동 팀장 전용. 최종 담당자 memberId 목록을 보냅니다. 빈 배열은 전체 해제. 실제 변경만 ASSIGN/UNASSIGN 이력으로 기록합니다.")
    public TaskResponse assign(@PathVariable("projectId") long projectId, @PathVariable("taskId") long taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AssigneeRequest request) {
        return tasks.setAssignees(projectId, taskId, userId(jwt), request);
    }

    @GetMapping("/{taskId}/assignee-histories")
    @Operation(summary = "담당자 변경 이력", description = "승인된 팀원 전용. 변경 시간·이력 ID 내림차순으로 반환합니다.")
    public List<AssigneeHistoryResponse> history(@PathVariable("projectId") long projectId, @PathVariable("taskId") long taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return tasks.history(projectId, taskId, userId(jwt));
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
