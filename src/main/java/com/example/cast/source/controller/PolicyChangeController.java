package com.example.cast.source.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.code.CommonSuccessCode;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.source.dto.response.PolicyChangeDetailResponse;
import com.example.cast.source.dto.response.PolicyChangeListResponse;
import com.example.cast.source.service.PolicyChangeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class PolicyChangeController implements PolicyChangeApi {
    private final PolicyChangeService service;

    @Override
    public ResponseEntity<ApiResponse<PolicyChangeListResponse>> list(
            @AuthenticationPrincipal AuthenticatedUser user,
            String keyword, Boolean requiresRecheck, int page, int size) {
        return ApiResponse.success(CommonSuccessCode.OK,
                service.list(user.userId(), keyword, requiresRecheck, page, size));
    }

    @Override
    public ResponseEntity<ApiResponse<PolicyChangeDetailResponse>> getDetail(
            @AuthenticationPrincipal AuthenticatedUser user, Long policyChangeId) {
        return ApiResponse.success(CommonSuccessCode.OK,
                service.getDetail(user.userId(), policyChangeId));
    }
}
