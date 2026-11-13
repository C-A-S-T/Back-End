
package com.example.cast.research.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.research.dto.response.ResearchResultResponse;
import com.example.cast.research.service.ResearchResultService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI Research", description = "AI 답변 생성 작업의 결과를 조회하는 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/research-tasks")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchResultService researchResultService;

    @Operation(
            summary = "AI 답변 결과 조회",
            description = "작업 ID(taskId)를 이용하여 AI 답변 생성 및 검증 결과를 조회합니다. "
                    + "JWT 인증이 필요하며, 본인이 생성한 작업만 조회할 수 있습니다."
    )
    @GetMapping("/{taskId}")
    public ResponseEntity<ResearchResultResponse> getResult(
            @Parameter(description = "조회할 AI 작업 ID", required = true)
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        Long userId = user.userId();

        return ResponseEntity.ok(
                researchResultService.getResult(userId, taskId)
        );
    }
}
