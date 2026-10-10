package com.risense.checkin;

import com.risense.checkin.CheckinData.*;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.media.*;
import com.risense.api.error.ApiError;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/checkins")
@Tag(name="체크인 기록",description="PENDING은 제출 기한이 남은 상태이며 누락으로 세지 않습니다.")
@SecurityRequirement(name="bearerAuth")
@ApiResponses({
    @ApiResponse(responseCode="400",description="INVALID_REQUEST / CHECKIN_INVALID_TASK_IDS / INVALID_ISSUE_CONTENT",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="401",description="UNAUTHORIZED",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="403",description="PROJECT_ACCESS_DENIED / CHECKIN_NOT_ELIGIBLE",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="404",description="PROJECT_NOT_FOUND / CHECKIN_ROUND_NOT_FOUND / CHECKIN_SUBMISSION_NOT_FOUND",content=@Content(schema=@Schema(implementation=ApiError.class))),
    @ApiResponse(responseCode="409",description="PROJECT_NOT_WRITABLE / CHECKIN_CLOSED / CHECKIN_LATE_EDIT_NOT_ALLOWED / CHECKIN_NO_TARGETS / CHECKIN_TARGETS_CHANGED / CHECKIN_TASK_CHANGED / CHECKIN_VERSION_CONFLICT / CHECKIN_REQUEST_KEY_REUSED / CHECKIN_ALREADY_SUBMITTED / CHECKIN_SUBMISSION_REQUIRED",content=@Content(schema=@Schema(implementation=ApiError.class)))
})
public class CheckinHistoryController {
    private final CheckinHistoryService service;
    public CheckinHistoryController(CheckinHistoryService service) { this.service=service; }
    @GetMapping("/{roundId}/status") @Operation(summary="회차별 팀 체크인 현황",description="승인된 팀원 전용. 유효 대상 없는 팀원은 제외합니다.")
    public Status status(@PathVariable long projectId,@PathVariable long roundId,@Parameter(hidden=true) @AuthenticationPrincipal Jwt jwt) {
        return service.status(projectId,roundId,Long.parseLong(jwt.getSubject()));
    }
    @GetMapping("/mine") @Operation(summary="내 체크인 기록",description="최신 회차부터 조회. nextBeforeRoundId를 다음 요청에 전달합니다.")
    public HistoryPage mine(@PathVariable long projectId,@Parameter(hidden=true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required=false) Long beforeRoundId,@RequestParam(defaultValue="20") int limit) {
        return service.mine(projectId,Long.parseLong(jwt.getSubject()),beforeRoundId,limit);
    }
    @GetMapping("/summary") @Operation(summary="프로젝트 체크인 누적 집계",description="제출자 수 합/대상자 수 합. 분모가 0이면 submissionRate=null이며 리스크 점수는 포함하지 않습니다.")
    public Summary summary(@PathVariable long projectId,@Parameter(hidden=true) @AuthenticationPrincipal Jwt jwt) {
        return service.aggregate(projectId,Long.parseLong(jwt.getSubject()));
    }
}
