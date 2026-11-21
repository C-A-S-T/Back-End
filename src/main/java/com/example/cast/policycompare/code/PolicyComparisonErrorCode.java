package com.example.cast.policycompare.code;

import com.example.cast.global.code.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 외부 AI 연동 실패 상세 정보를 노출하지 않는 정책 비교 전용 오류. */
@Getter
@RequiredArgsConstructor
public enum PolicyComparisonErrorCode implements ErrorCode {
    AI_BAD_GATEWAY(HttpStatus.BAD_GATEWAY, "정책 비교 결과를 가져오지 못했습니다."),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "정책 비교 서비스를 이용할 수 없습니다."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "정책 비교 서비스 응답 시간이 초과되었습니다.");

    private final HttpStatus status;
    private final String message;
}
