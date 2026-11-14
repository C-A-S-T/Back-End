package com.example.cast.source.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.config.SwaggerConfig;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.source.dto.response.PolicyChangeListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Tag(name = "Source Archive", description = "사용자의 출처보관함 조회")
public interface PolicyChangeApi {
    @Operation(summary = "정책 변경 카드 목록 조회",
            description = """
                    수집된 전체 정책의 확인된 문서 버전 비교 이력을 조회합니다.
                    사용자가 문서를 직접 보관하지 않아도 조회할 수 있습니다.
                    keyword는 정책명 부분 검색(최대 100자)이며, 영문 대소문자를 구별하지 않습니다.
                    requiresRecheck=true/false는 로그인 사용자에게 평가된 결과만 필터링하며,
                    영향 평가가 없으면 requiresRecheck와 impactSummary는 null입니다.
                    필터를 생략하면 미평가 카드도 표시합니다.
                    확인 최신순이며 페이지는 0부터 시작하고 크기는 1~100(기본 20)입니다.
                    정책 비교 AI나 변경 정보 등록 기능은 이 API에 포함되지 않습니다.
                    """,
            security = @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME))
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200", description = "변경 이력 카드 목록 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "NO_CONFIRMED_CHANGES", value = EMPTY_RESULT))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "검색어, 필터 또는 페이지 값 오류",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "JWT 인증 필요",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))
            )
    })
    @GetMapping("/policy-changes")
    ResponseEntity<ApiResponse<PolicyChangeListResponse>> list(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user,
            @Parameter(description = "정책명 부분 검색어 (최대 100자)", example = "청년")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "현재 사용자의 재확인 필요 판정 필터. 생략 시 미평가 기록 포함")
            @RequestParam(required = false) Boolean requiresRecheck,
            @Parameter(description = "0부터 시작하는 페이지 번호", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지당 카드 수 (1~100)", example = "20")
            @RequestParam(defaultValue = "20") int size);

    String EMPTY_RESULT = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "timestamp": "2026-11-14T23:00:00",
              "result": {
                "items": [],
                "page": 0,
                "size": 20,
                "totalElements": 0,
                "totalPages": 0,
                "hasNext": false
              }
            }
            """;
}
