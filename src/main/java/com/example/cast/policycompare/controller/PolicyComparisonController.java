package com.example.cast.policycompare.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.code.CommonSuccessCode;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.policycompare.dto.response.PolicyComparisonResponse;
import com.example.cast.policycompare.service.PolicyComparisonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policy-comparisons")
@RequiredArgsConstructor
public class PolicyComparisonController implements PolicyComparisonApi {
    private final PolicyComparisonService service;

    @Override
    public ResponseEntity<ApiResponse<PolicyComparisonResponse>> getComparison(
            @AuthenticationPrincipal AuthenticatedUser user, String policyKey) {
        return ApiResponse.success(CommonSuccessCode.OK, service.getComparison(policyKey));
    }
}
