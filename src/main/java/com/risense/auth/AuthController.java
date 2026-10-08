package com.risense.auth;

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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "인증", description = "이메일 회원가입, 로그인 및 내 정보 조회")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth) { this.auth = auth; }

    @Operation(summary = "회원가입", description = "이메일은 앞뒤 공백 제거 및 소문자로 저장합니다. 가입 후 별도로 로그인해야 합니다. 비밀번호는 8~72자이며 UTF-8 72바이트 이하여야 합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "가입 성공", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 입력 오류 / INVALID_PASSWORD: 72바이트 초과", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "EMAIL_ALREADY_EXISTS: 이메일 중복", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody AuthRequests.Register request) {
        return auth.register(request);
    }

    @Operation(summary = "로그인", description = "발급된 accessToken을 Authorization: Bearer <token> 헤더로 전달합니다. expiresIn은 초 단위이며 기본 3600입니다. Refresh token은 제공하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 성공", content = @Content(schema = @Schema(implementation = LoginResponse.class))),
            @ApiResponse(responseCode = "400", description = "INVALID_REQUEST: 입력 오류 / INVALID_PASSWORD: 72바이트 초과", content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "401", description = "INVALID_CREDENTIALS: 이메일 또는 비밀번호 불일치", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody AuthRequests.Login request) {
        return auth.login(request);
    }

    @Operation(summary = "내 정보 조회", description = "유효한 로그인 토큰이 필요합니다.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공", content = @Content(schema = @Schema(implementation = UserResponse.class))),
            @ApiResponse(responseCode = "401", description = "UNAUTHORIZED: 토큰 누락·만료·오류 또는 사용자 없음", content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    @GetMapping("/me")
    public UserResponse me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt principal) {
        return auth.currentUser(Long.parseLong(principal.getSubject()));
    }
}
