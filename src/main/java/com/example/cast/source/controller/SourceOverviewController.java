package com.example.cast.source.controller;

import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.global.code.CommonSuccessCode;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.source.dto.response.SourceOverviewResponse;
import com.example.cast.source.service.SourceOverviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sources")
@RequiredArgsConstructor
public class SourceOverviewController implements SourceOverviewApi {
    private final SourceOverviewService overviewService;

    @Override
    public ResponseEntity<ApiResponse<SourceOverviewResponse>> overview(
            @AuthenticationPrincipal AuthenticatedUser user
    ) {
        return ApiResponse.success(CommonSuccessCode.OK, overviewService.getOverview(user.userId()));
    }
}
