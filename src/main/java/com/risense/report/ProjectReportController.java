package com.risense.report;

import com.risense.api.error.ApiError;
import io.swagger.v3.oas.annotations.*;
import io.swagger.v3.oas.annotations.media.*;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/projects/{projectId}/report")
@Tag(name="프로젝트 리포트")
@SecurityRequirement(name="bearerAuth")
public class ProjectReportController {
    private final ProjectReportService reports;
    public ProjectReportController(ProjectReportService reports) { this.reports=reports; }
    @GetMapping
    @Operation(summary="프로젝트 최종 리포트",description="승인된 팀원 전용. 진행 중에도 조회 가능하고 종료 프로젝트도 조회 가능합니다. 종료 프로젝트의 기한 판정은 closedAt 기준입니다. 공동 담당 작업은 각 담당자에게 표시되므로 팀원별 작업 수 합은 프로젝트 합계와 다를 수 있습니다.")
    @ApiResponses({
        @ApiResponse(responseCode="200",description="실제 데이터 집계. 체크인 대상 0건이면 submissionRate=null",content=@Content(mediaType="application/json",schema=@Schema(implementation=ReportResponse.class))),
        @ApiResponse(responseCode="400",description="INVALID_REQUEST",content=@Content(schema=@Schema(implementation=ApiError.class))),
        @ApiResponse(responseCode="401",description="로그인 필요",content=@Content(schema=@Schema(implementation=ApiError.class))),
        @ApiResponse(responseCode="403",description="PROJECT_ACCESS_DENIED",content=@Content(schema=@Schema(implementation=ApiError.class))),
        @ApiResponse(responseCode="404",description="PROJECT_NOT_FOUND",content=@Content(schema=@Schema(implementation=ApiError.class)))
    })
    public ReportResponse report(@PathVariable("projectId") long projectId,
            @Parameter(hidden=true) @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name="eventLimit",defaultValue="100") int eventLimit) {
        return reports.report(projectId,Long.parseLong(jwt.getSubject()),eventLimit);
    }
}
