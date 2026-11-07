package com.example.cast.global.code;
import org.springframework.http.HttpStatus;
/** HTTP 상태와 외부에 공개할 메시지의 공통 계약. */
public interface SuccessCode {
    HttpStatus getStatus();
    String getMessage();
}
