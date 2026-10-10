package com.risense.checkin;

import com.risense.checkin.CheckinData.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import com.risense.api.error.ApiError;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/checkins")
@Tag(name="체크인",description="KST 회차와 전체 대상 제출. 현재 정보와 제출 당시 스냅샷을 구분합니다.")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode="400",description="INVALID_REQUEST / CHECKIN_INVALID_TASK_IDS / INVALID_ISSUE_CONTENT",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="401",description="UNAUTHORIZED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="PROJECT_ACCESS_DENIED / CHECKIN_NOT_ELIGIBLE",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="PROJECT_NOT_FOUND / CHECKIN_ROUND_NOT_FOUND / CHECKIN_SUBMISSION_NOT_FOUND",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="409",description="PROJECT_NOT_WRITABLE / CHECKIN_CLOSED / CHECKIN_LATE_EDIT_NOT_ALLOWED / CHECKIN_NO_TARGETS / CHECKIN_TARGETS_CHANGED / CHECKIN_TASK_CHANGED / CHECKIN_VERSION_CONFLICT / CHECKIN_REQUEST_KEY_REUSED / CHECKIN_ALREADY_SUBMITTED / CHECKIN_SUBMISSION_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class CheckinController {
    private final CheckinService service;
    public CheckinController(CheckinService service) { this.service=service; }
    @GetMapping("/current") @Operation(summary="현재 체크인 조회",description="회차가 없으면 round=null. canSubmit/canEdit와 unavailableReason으로 버튼 상태를 결정합니다.")
    public Current current(@PathVariable long projectId,@Parameter(hidden=true) @AuthenticationPrincipal Jwt jwt) {
        return service.current(projectId,Long.parseLong(jwt.getSubject()));
    }
    @GetMapping("/{roundId}/submission") @Operation(summary="내 제출 스냅샷 조회")
    public Submission detail(@PathVariable long projectId,@PathVariable long roundId,@Parameter(hidden=true) @AuthenticationPrincipal Jwt jwt) {
        return service.detail(projectId,roundId,Long.parseLong(jwt.getSubject()));
    }
    @PostMapping("/{roundId}/submission") @Operation(summary="최초 체크인 제출",description="전체 대상과 조회한 버전을 전송합니다. expectedRevision=0. 같은 requestKey/본문 재전송은 기존 응답을 반환합니다. 충돌 시 409.")
    public Submission create(@PathVariable long projectId,@PathVariable long roundId,@Parameter(hidden=true) @AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Submit request) {
        return service.submit(projectId,roundId,Long.parseLong(jwt.getSubject()),request,false);
    }
    @PutMapping("/{roundId}/submission") @Operation(summary="정시 체크인 수정",description="최신 expectedRevision과 새 requestKey가 필요합니다. 최초 제출 시각은 유지하며 지각 기간에는 수정할 수 없습니다.")
    public Submission update(@PathVariable long projectId,@PathVariable long roundId,@Parameter(hidden=true) @AuthenticationPrincipal Jwt jwt,@Valid @RequestBody Submit request) {
        return service.submit(projectId,roundId,Long.parseLong(jwt.getSubject()),request,true);
    }
}
