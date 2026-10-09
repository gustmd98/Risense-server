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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/invites")
@Tag(name = "초대 가입")
@ApiResponses({
        @ApiResponse(responseCode = "404", description = "INVITE_NOT_FOUND", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "409", description = "PROJECT_NOT_WRITABLE", content = @Content(schema = @Schema(implementation = ApiError.class))),
        @ApiResponse(responseCode = "410", description = "INVITE_UNAVAILABLE: 만료 또는 비활성화", content = @Content(schema = @Schema(implementation = ApiError.class)))
})
public class InviteController {
    private final TeamService team;
    public InviteController(TeamService team) { this.team = team; }

    @GetMapping("/{token}")
    @Operation(summary = "초대 링크 검증", description = "로그인 없이 유효성·프로젝트 이름만 확인합니다.")
    public InvitePreview preview(@PathVariable("token") String token) { return team.preview(token); }

    @PostMapping("/{token}/join")
    @Operation(summary = "가입 요청", description = "로그인 필요. PENDING으로 요청합니다. 대기·승인 상태 재요청은 기존 내역을 반환합니다. 거절·내보내기 후에도 유효한 초대 링크로 재신청 가능합니다. 일반 팀원 역할의 승인 대기 상태로 전환되며 팀장 승인이 필요합니다.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "401", description = "로그인 필요", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public MemberResponse join(@PathVariable("token") String token,
            @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
        return team.join(token, Long.parseLong(jwt.getSubject()));
    }
}
