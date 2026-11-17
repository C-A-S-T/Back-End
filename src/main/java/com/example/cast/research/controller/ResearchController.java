package com.example.cast.research.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.code.CommonSuccessCode;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.research.dto.request.ResearchInputs;
import com.example.cast.research.dto.response.ResearchEvidenceResponse;
import com.example.cast.research.dto.response.ResearchResultResponse;
import com.example.cast.research.dto.response.ResearchStartResponse;
import com.example.cast.research.service.ResearchEvidenceService;
import com.example.cast.research.service.ResearchResultService;
import com.example.cast.research.service.ResearchStartService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(
        name = "AI Research",
        description = "AI 연구 작업 생성 및 결과 조회 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/research-tasks")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchResultService researchResultService;
    private final ResearchStartService researchStartService;
    private final ResearchEvidenceService evidenceService;

    @Operation(
            summary = "AI 연구 시작",
            description = "질문을 받아 AI 연구 작업을 생성하고 queryId와 taskId를 반환합니다. JWT 인증이 필요합니다."
    )
    @PostMapping
    public ResponseEntity<ApiResponse<ResearchStartResponse>> startResearch(
            @Valid @RequestBody ResearchInputs.Query input,
            Authentication authentication
    ) {
        Long userId = authenticatedUserId(authentication);

        return ApiResponse.success(
                CommonSuccessCode.OK,
                researchStartService.start(userId, input)
        );
    }

    @Operation(
            summary = "AI 연구 결과 조회",
            description = "작업 ID로 AI 연구 결과를 조회합니다. 본인이 생성한 작업만 조회할 수 있습니다."
    )
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<ResearchResultResponse>> getResult(
            @Parameter(description = "조회할 AI 작업 ID", required = true)
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        Long userId = authenticatedUserId(authentication);

        return ApiResponse.success(
                CommonSuccessCode.OK,
                researchResultService.getResult(userId, taskId)
        );
    }

    @Operation(
            summary = "AI 연구 근거 원문 조회",
            description = "완료된 연구의 주장별 최종 근거와 현재 스크랩 상태를 반환합니다."
    )
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "근거 원문 조회 성공",
                    useReturnTypeSchema = true
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400", description = "작업 ID 오류"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401", description = "인증 필요"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404", description = "다른 사용자의 작업 또는 존재하지 않는 작업"
            )
    })
    @GetMapping("/{taskId}/evidences")
    public ResponseEntity<ApiResponse<ResearchEvidenceResponse>> getEvidences(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        Long userId = authenticatedUserId(authentication);

        return ApiResponse.success(
                CommonSuccessCode.OK,
                evidenceService.getEvidences(userId, taskId)
        );
    }

    private Long authenticatedUserId(Authentication authentication) {
        if (authentication == null
                || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        return user.userId();
    }
}