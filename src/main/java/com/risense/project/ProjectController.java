package com.risense.project;

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
@RequestMapping("/api/projects")
@Tag(name = "프로젝트", description = "프로젝트와 체크인 설정 관리")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "입력 오류 또는 INVALID_CHECKIN_SCHEDULE", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGER_REQUIRED", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "PROJECT_NOT_WRITABLE: 완료·종료 후 수정 불가", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class ProjectController {
    private final ProjectService projects;
    public ProjectController(ProjectService projects) { this.projects = projects; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "프로젝트 생성", description = "생성자를 승인된 팀장으로 자동 등록합니다. 체크인 시간은 프로젝트 설정의 현지 시간입니다.")
    public ProjectResponse create(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProjectRequest request) {
        return projects.create(Long.parseLong(jwt.getSubject()), request);
    }

    @GetMapping
    @Operation(summary = "내 프로젝트 목록", description = "승인된 멤버로 참여한 프로젝트를 생성일 내림차순으로 반환합니다. 종료된 프로젝트도 포함합니다.")
    public List<ProjectResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return projects.list(Long.parseLong(jwt.getSubject()));
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "프로젝트 상세", description = "승인된 팀원만 조회할 수 있습니다.")
    public ProjectResponse detail(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return projects.detail(projectId, Long.parseLong(jwt.getSubject()));
    }

    @PutMapping("/{projectId}")
    @Operation(summary = "프로젝트 설정 수정", description = "팀장·공동 팀장 전용. 모든 설정을 전달합니다. 완료·종료 상태에서는 409를 반환합니다.")
    public ProjectResponse update(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ProjectRequest request) {
        return projects.update(projectId, Long.parseLong(jwt.getSubject()), request);
    }

    @PostMapping("/{projectId}/close")
    @Operation(summary = "프로젝트 종료", description = "팀장·공동 팀장 전용. CLOSED로 전환하며 다시 호출해도 최초 종료 시간을 유지합니다. 재개 API는 없습니다.")
    public ProjectResponse close(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return projects.close(projectId, Long.parseLong(jwt.getSubject()));
    }
}
