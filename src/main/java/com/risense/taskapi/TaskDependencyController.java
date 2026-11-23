package com.risense.taskapi;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks/dependencies")
@Tag(name = "작업 관계·하위 작업·산출물")
@SecurityRequirement(name = "bearerAuth")
public class TaskDependencyController {
    private final TaskRelationService relations;
    public TaskDependencyController(TaskRelationService relations) { this.relations = relations; }
    @GetMapping
    @Operation(summary = "프로젝트 전체 선행 관계", description = "승인된 팀원 전용. prerequisiteTaskId가 먼저 끝나야 할 작업입니다. 취소 작업을 포함한 저장된 관계를 반환합니다.")
    @ApiResponse(responseCode="200", description="관계 배열. 없으면 []", content=@Content(mediaType="application/json", array=@ArraySchema(schema=@Schema(implementation=TaskRelationService.Dependency.class))))
    public List<TaskRelationService.Dependency> list(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return relations.dependencies(projectId, Long.parseLong(jwt.getSubject()));
    }
}
