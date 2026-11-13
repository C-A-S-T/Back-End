package com.example.cast.research.controller;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.research.dto.response.ResearchResultResponse;
import com.example.cast.research.service.ResearchResultService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/research-tasks")
@RequiredArgsConstructor
public class ResearchController {

    private final ResearchResultService researchResultService;

    @GetMapping("/{taskId}")
    public ResponseEntity<ResearchResultResponse> getResult(
            @PathVariable Long taskId,
            Authentication authentication
    ) {
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof Long userId)) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        return ResponseEntity.ok(
                researchResultService.getResult(userId, taskId)
        );
    }
}