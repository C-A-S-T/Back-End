
package com.example.cast.global.code;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답 코드의 공통 계약.
 *
 * HTTP 상태와 애플리케이션 응답 코드를 구분한다.
 */
public interface ErrorCode {

    HttpStatus getStatus();

    String getMessage();

    /**
     * 기본 오류 코드는 COMMON + HTTP 상태 코드.
     * 도메인별 코드가 필요한 경우 구현체에서 재정의할 수 있다.
     */
    default String getCode() {
        return "COMMON" + getStatus().value();
    }
}
