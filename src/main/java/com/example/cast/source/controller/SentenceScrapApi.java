package com.example.cast.source.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.claim.entity.VerificationStatus;
import com.example.cast.document.entity.SourceType;
import com.example.cast.global.config.SwaggerConfig;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.source.dto.request.SentenceScrapCreateRequest;
import com.example.cast.source.dto.response.SentenceScrapListResponse;
import com.example.cast.source.dto.response.SentenceScrapSaveResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Source Archive", description = "사용자의 출처보관함 조회")
public interface SentenceScrapApi {
    @Operation(summary = "근거 원문 스크랩",
            description = "본인 리서치의 완료된 Evidence를 저장합니다. 최초 생성은 201, 중복 요청은 기존 기록을 200으로 반환합니다.",
            security = @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                    description = "스크랩 신규 저장", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "이미 저장한 근거 (중복 생성 없음)", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                    description = "잘못된 근거 ID", content = @Content(schema = @Schema(implementation = ApiResponse.class))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                    description = "근거 없음 또는 다른 사용자 소유")
    })
    @PostMapping("/scraps")
    ResponseEntity<ApiResponse<SentenceScrapSaveResponse>> save(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody SentenceScrapCreateRequest request);

    @Operation(summary = "스크랩 문장 목록 조회",
            description = "문장·출처·주장 검색, 검증 결과·출처 유형·고정 폴더 필터, 최신순 페이지네이션을 지원합니다.",
            security = @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                    description = "스크랩 목록 조회", useReturnTypeSchema = true),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "필터 또는 페이지 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "타인 또는 없는 폴더")
    })
    @GetMapping("/scraps")
    ResponseEntity<ApiResponse<SentenceScrapListResponse>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) VerificationStatus verificationStatus,
            @RequestParam(required = false) SourceType sourceType,
            @RequestParam(required = false) Long folderId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size);

    @Operation(summary = "스크랩 해제", description = "사용자의 스크랩 기록만 삭제하며 Evidence는 유지합니다.",
            security = @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "스크랩 삭제"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 스크랩 ID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "스크랩 없음 또는 다른 사용자 소유")
    })
    @DeleteMapping("/scraps/{scrapId}")
    ResponseEntity<ApiResponse<Map<String, Object>>> delete(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "사용자 스크랩 ID") @PathVariable Long scrapId);
}
