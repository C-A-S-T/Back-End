
package com.example.cast.global.code;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * 도메인에 종속되지 않는 공통 성공 코드.
 */
@Getter
@RequiredArgsConstructor
public enum CommonSuccessCode implements SuccessCode {

    OK(
            HttpStatus.OK,
            "성공입니다."
    ),

    CREATED(
            HttpStatus.CREATED,
            "생성되었습니다."
    ),

    ACCEPTED(
            HttpStatus.ACCEPTED,
            "요청이 접수되었습니다."
    );

    private final HttpStatus status;
    private final String message;
}
