package com.risense.team;

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
@RequestMapping("/api/projects/{projectId}")
@Tag(name = "팀 관리")
@SecurityRequirement(name = "bearerAuth")
@ApiResponses({
        @ApiResponse(responseCode = "400", description = "INVALID_REQUEST / INVALID_INVITE_EXPIRY", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "403", description = "PROJECT_ACCESS_DENIED / PROJECT_MANAGER_REQUIRED / LEADER_REQUIRED", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "404", description = "PROJECT_NOT_FOUND / MEMBER_NOT_FOUND / INVITE_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "PROJECT_NOT_WRITABLE / INVALID_MEMBER_STATUS / LAST_LEADER", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class TeamController {
    private final TeamService team;
    public TeamController(TeamService team) { this.team = team; }

    @PostMapping("/invite-links")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "초대 링크 발급", description = "팀장·공동 팀장 전용. 기존 활성 링크는 무효화합니다. token으로 프론트 초대 URL을 구성합니다. 기본 만료 168시간.")
    public InviteResponse issue(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) TeamRequests.Invite request) {
        return team.issue(projectId, userId(jwt), request);
    }

    @GetMapping("/invite-links/current")
    @Operation(summary = "현재 유효한 초대 링크 조회", description = "팀장·공동 팀장 전용.")
    public InviteResponse current(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return team.currentInvite(projectId, userId(jwt));
    }

    @DeleteMapping("/invite-links/{inviteId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "초대 링크 비활성화", description = "팀장·공동 팀장 전용. 이미 비활성화된 링크는 그대로 유지합니다.")
    public void revoke(@PathVariable("projectId") long projectId, @PathVariable("inviteId") long inviteId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        team.revoke(projectId, inviteId, userId(jwt));
    }

    @GetMapping("/membership")
    @Operation(summary = "내 가입 상태 조회", description = "승인 대기 화면에서 사용합니다. 본인의 PENDING/APPROVED/REJECTED/REMOVED 상태만 조회합니다.")
    public MemberResponse membership(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return team.myMembership(projectId, userId(jwt));
    }

    @GetMapping("/members")
    @Operation(summary = "승인된 팀원 목록", description = "승인된 팀원만 조회 가능합니다. 이메일은 공개하지 않습니다.")
    public List<MemberResponse> members(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return team.list(projectId, userId(jwt));
    }

    @GetMapping("/join-requests")
    @Operation(summary = "가입 승인 대기 목록", description = "팀장·공동 팀장 전용.")
    public List<MemberResponse> pending(@PathVariable("projectId") long projectId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return team.pending(projectId, userId(jwt));
    }

    @PostMapping("/members/{memberId}/approve")
    @Operation(summary = "가입 요청 승인", description = "팀장·공동 팀장 전용. 이미 승인된 요청은 최초 승인 시각을 유지합니다.")
    public MemberResponse approve(@PathVariable("projectId") long projectId, @PathVariable("memberId") long memberId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return team.approve(projectId, memberId, userId(jwt));
    }

    @PostMapping("/members/{memberId}/reject")
    @Operation(summary = "가입 요청 거절", description = "팀장·공동 팀장 전용. 승인된 팀원의 가입을 거절 상태로 되돌릴 수 없습니다.")
    public MemberResponse reject(@PathVariable("projectId") long projectId, @PathVariable("memberId") long memberId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return team.reject(projectId, memberId, userId(jwt));
    }

    @PatchMapping("/members/{memberId}/role")
    @Operation(summary = "팀원 역할 변경", description = "팀장 전용. 승인된 팀원만 변경 가능하며 마지막 팀장은 강등할 수 없습니다.")
    public MemberResponse role(@PathVariable("projectId") long projectId, @PathVariable("memberId") long memberId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TeamRequests.Role request) {
        return team.changeRole(projectId, memberId, userId(jwt), request.role());
    }

    @DeleteMapping("/members/{memberId}")
    @Operation(summary = "팀원 내보내기", description = "팀장은 모든 역할, 공동 팀장은 일반 팀원만 내보낼 수 있습니다. 마지막 팀장은 제거 불가. DB 행을 보존하고 REMOVED로 처리합니다.")
    public MemberResponse remove(@PathVariable("projectId") long projectId, @PathVariable("memberId") long memberId,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return team.remove(projectId, memberId, userId(jwt));
    }

    private long userId(Jwt jwt) { return Long.parseLong(jwt.getSubject()); }
}
