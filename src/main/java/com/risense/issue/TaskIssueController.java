package com.risense.issue;

import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/tasks/{taskId}/issue")
@Tag(name = "작업 이슈", description = "현재 이슈 조회 및 체크인 밖의 해결 처리")
@SecurityRequirement(name = "bearerAuth")
public class TaskIssueController {
    private final TaskIssueService issues;
    public TaskIssueController(TaskIssueService issues) { this.issues = issues; }
    public record Resolve(@NotNull @PositiveOrZero Long expectedRevision) {}
    @GetMapping
    @Operation(summary = "작업 이슈 조회", description = "승인된 팀원 전용. 이슈가 없으면 open=false, revision=0.")
    public IssueResponse detail(@PathVariable long projectId, @PathVariable long taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return issues.detail(projectId, taskId, Long.parseLong(jwt.getSubject()));
    }
    @PatchMapping("/resolve")
    @Operation(summary = "작업 이슈 해결", description = "작업 담당자·팀장·공동 팀장 전용. 체크인 제출 기간과 독립적이며 프로젝트 종료 후에는 불가. expectedRevision 불일치 시 409.")
    public IssueResponse resolve(@PathVariable long projectId, @PathVariable long taskId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Resolve request) {
        return issues.resolve(projectId, taskId, Long.parseLong(jwt.getSubject()), request.expectedRevision());
    }
}
