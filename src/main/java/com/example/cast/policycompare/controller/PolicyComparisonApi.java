package com.example.cast.policycompare.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.config.SwaggerConfig;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Tag(name = "Policy Comparisons", description = "정책 비교 결과 조회 (검토 상태 포함)")
public interface PolicyComparisonApi {
    @Operation(summary = "정책 비교 조회",
            description = """
                    JWT로 로그인한 사용자가 AI의 정책 비교 결과와 항목별 PDF 근거를 조회합니다.
                    현재 policyKey는 seoul-youth-rent만 지원합니다.
                    현재 제공되는 비교 결과는 REVIEW_REQUIRED 상태이며 확정된 정책 안내가 아닙니다.
                    researchImpact는 주요 변경 후보와 보조 근거의 공통 안내이며 사용자별 판정이 아닙니다.
                    기존 items의 전체 비교 및 미승인 상태는 유지됩니다.
                    DB 저장, AI 재실행, 승인 처리를 수행하지 않습니다.
                    """,
            security = @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "정책 비교 결과 조회 성공",
                    useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "사용자 JWT 필요",
                    content = @io.swagger.v3.oas.annotations.media.Content(
                            schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "지원하지 않거나 존재하지 않는 정책 키"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "502", description = "AI 결과 또는 서버 간 인증 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "503", description = "AI 미설정 또는 일시적 서비스 불가"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "504", description = "AI 요청 시간 초과")
    })
    @GetMapping("/{policyKey}")
    ResponseEntity<ApiResponse<PolicyComparisonResponse>> getComparison(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "현재 지원하는 정책 키", example = "seoul-youth-rent")
            @PathVariable String policyKey);
}
