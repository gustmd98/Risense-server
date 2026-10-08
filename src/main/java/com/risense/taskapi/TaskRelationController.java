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
@RequestMapping("/api/projects/{projectId}/tasks/{taskId}")
@Tag(name = "작업 관계·하위 작업·산출물")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST / INVALID_PREREQUISITES / INVALID_ASSIGNEES / INVALID_ARTIFACT_URL", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "프로젝트 권한 없음 / SUBTASK_ASSIGNEE_REQUIRED / ARTIFACT_OWNER_REQUIRED", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / TASK_NOT_FOUND / SUBTASK_NOT_FOUND / ARTIFACT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "PROJECT_NOT_WRITABLE / TASK_CANCELLED / TASK_DEPENDENCY_CYCLE / SUBTASK_ORDER_LIMIT", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TaskRelationController {
    private final TaskRelationService relations;
    public TaskRelationController(TaskRelationService relations) { this.relations = relations; }

    @GetMapping("/prerequisites")
    @Operation(summary = "선행 작업 목록", description = "승인된 팀원 전용.")
    public List<RelationResponses.Prerequisite> prerequisites(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return relations.prerequisites(projectId, taskId, userId(jwt));
    }

    @PutMapping("/prerequisites")
    @Operation(summary = "선행 작업 변경", description = "팀장·공동 팀장 전용. 최종 작업 ID 목록을 지정합니다. 빈 배열은 해제. 자기 참조·중복·다른 프로젝트·취소 작업·순환 관계를 차단합니다.")
    public List<RelationResponses.Prerequisite> prerequisites(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RelationRequests.Prerequisites request) {
        return relations.setPrerequisites(projectId, taskId, userId(jwt), request);
    }

    @GetMapping("/sub-tasks")
    @Operation(summary = "하위 작업 목록", description = "승인된 팀원 전용. 정렬 순서·ID 오름차순.")
    public List<RelationResponses.Child> children(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return relations.children(projectId, taskId, userId(jwt));
    }

    @PostMapping("/sub-tasks")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "하위 작업 생성", description = "팀장·공동 팀장 전용. completed=false로 생성합니다.")
    public RelationResponses.Child createChild(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RelationRequests.SubTaskSettings request) {
        return relations.createChild(projectId, taskId, userId(jwt), request);
    }

    @PutMapping("/sub-tasks/{subTaskId}")
    @Operation(summary = "하위 작업 설정 변경", description = "팀장·공동 팀장 전용. assigneeMemberId=null이면 담당자 해제, sortOrder=null이면 기존 순서 유지.")
    public RelationResponses.Child updateChild(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @PathVariable("subTaskId") long childId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RelationRequests.SubTaskSettings request) {
        return relations.updateChild(projectId, taskId, childId, userId(jwt), request);
    }

    @PatchMapping("/sub-tasks/{subTaskId}/completion")
    @Operation(summary = "하위 작업 완료 표시", description = "관리자 또는 해당 담당자 전용. 부모 작업 진행률·상태는 직접 변경하지 않습니다.")
    public RelationResponses.Child completeChild(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @PathVariable("subTaskId") long childId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RelationRequests.Completion request) {
        return relations.completeChild(projectId, taskId, childId, userId(jwt), request);
    }

    @DeleteMapping("/sub-tasks/{subTaskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "하위 작업 삭제", description = "팀장·공동 팀장 전용. 부모 작업과 담당자 변경 이력은 보존합니다.")
    public void deleteChild(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @PathVariable("subTaskId") long childId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        relations.deleteChild(projectId, taskId, childId, userId(jwt));
    }

    @GetMapping("/artifacts")
    @Operation(summary = "산출물 목록", description = "승인된 팀원 전용. 생성 시간·ID 내림차순.")
    public List<RelationResponses.Artifact> artifacts(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return relations.artifacts(projectId, taskId, userId(jwt));
    }

    @PostMapping("/artifacts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "산출물 링크 등록", description = "승인된 팀원 전용. HTTP/HTTPS 링크를 저장하며 서버에서 링크 내용을 불러오지 않습니다.")
    public RelationResponses.Artifact createArtifact(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody RelationRequests.Artifact request) {
        return relations.createArtifact(projectId, taskId, userId(jwt), request);
    }

    @PutMapping("/artifacts/{artifactId}")
    @Operation(summary = "산출물 링크 수정", description = "작성자 또는 관리자 전용. 최초 작성자·작성 시간은 유지합니다.")
    public RelationResponses.Artifact updateArtifact(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @PathVariable("artifactId") long artifactId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody RelationRequests.Artifact request) {
        return relations.updateArtifact(projectId, taskId, artifactId, userId(jwt), request);
    }

    @DeleteMapping("/artifacts/{artifactId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "산출물 링크 삭제", description = "작성자 또는 관리자 전용.")
    public void deleteArtifact(@PathVariable("projectId") long projectId,
            @PathVariable("taskId") long taskId, @PathVariable("artifactId") long artifactId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        relations.deleteArtifact(projectId, taskId, artifactId, userId(jwt));
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
