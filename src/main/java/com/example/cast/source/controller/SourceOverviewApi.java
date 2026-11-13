package com.example.cast.source.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.config.SwaggerConfig;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.source.dto.response.SourceOverviewResponse;
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

/** 출처보관함 조회 API의 Swagger 계약. */
@Tag(name = "Source Archive", description = "사용자의 출처보관함 조회")
public interface SourceOverviewApi {

    @Operation(
            summary = "출처보관함 개요 조회",
            description = """
                    로그인한 사용자의 저장 문서 수와 고정 폴더별 문서 수를 조회합니다.
                    전체 저장 문서 수는 자동·수동 저장 모두 포함하며,
                    자동 저장 문서 수는 최초 저장 방식이 AUTO인 문서만 포함합니다.
                    미분류 문서는 folderId가 null인 저장 문서입니다.
                    스크랩 문장 수와 변경된 정책 수는 아직 집계 기능이 없어 null을 반환합니다.
                    """,
            security = @SecurityRequirement(name = SwaggerConfig.JWT_SCHEME)
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "출처보관함 개요 조회 성공",
                    useReturnTypeSchema = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "OVERVIEW_SUCCESS",
                                    value = OVERVIEW_SUCCESS
                            )
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "JWT 미입력·만료·검증 실패",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(
                                    name = "UNAUTHORIZED",
                                    value = UNAUTHORIZED
                            )
                    )
            )
    })
    @GetMapping("/overview")
    ResponseEntity<ApiResponse<SourceOverviewResponse>> overview(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser user
    );

    String OVERVIEW_SUCCESS = """
            {
              "isSuccess": true,
              "code": "COMMON200",
              "message": "성공입니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {
                "totalSavedDocumentCount": 24,
                "autoSavedDocumentCount": 20,
                "scrappedSentenceCount": null,
                "changedPolicyCount": null,
                "folderCount": 3,
                "unclassifiedDocumentCount": 2,
                "legacyFolderDocumentCount": 0,
                "folders": [
                  {"folderId": 1, "category": "HOUSING", "folderName": "청약·주거", "documentCount": 12},
                  {"folderId": 2, "category": "GOV_SUPPORT", "folderName": "정부 지원정책", "documentCount": 7},
                  {"folderId": 3, "category": "EDUCATION", "folderName": "교육", "documentCount": 3}
                ]
              }
            }
            """;

    String UNAUTHORIZED = """
            {
              "isSuccess": false,
              "code": "COMMON401",
              "message": "인증이 필요합니다.",
              "timestamp": "2026-11-14T15:30:00",
              "result": {}
            }
            """;
}
