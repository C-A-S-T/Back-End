
package com.example.cast.research.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.code.CommonSuccessCode;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.research.dto.request.ResearchInputs;
import com.example.cast.research.dto.response.ResearchResultResponse;
import com.example.cast.research.dto.response.ResearchStartResponse;
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
        description = "AI 리서치 작업 생성 및 결과 조회 API"
)
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/research-tasks")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchResultService researchResultService;
    private final ResearchStartService researchStartService;

    @Operation(
            summary = "AI 리서치 시작",
            description = "질문을 받아 AI 리서치 작업을 생성하고 "
                    + "queryId와 taskId를 반환합니다. JWT 인증이 필요합니다."
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
            summary = "AI 답변 결과 조회",
            description = "작업 ID(taskId)를 이용하여 AI 답변 생성 및 검증 결과를 조회합니다. "
                    + "JWT 인증이 필요하며, 본인이 생성한 작업만 조회할 수 있습니다."
    )
    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<ResearchResultResponse>> getResult(
            @Parameter(
                    description = "조회할 AI 작업 ID",
                    required = true
            )
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        Long userId = authenticatedUserId(authentication);

        return ApiResponse.success(
                CommonSuccessCode.OK,
                researchResultService.getResult(userId, taskId)
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
