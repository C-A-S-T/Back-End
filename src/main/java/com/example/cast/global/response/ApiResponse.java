package com.example.cast.global.response;
import com.example.cast.global.code.ErrorCode;
import com.example.cast.global.code.SuccessCode;
import org.springframework.http.ResponseEntity;
/** HTTP 상태와 본문의 상태를 한 곳에서 결정하는 공통 응답. */
public record ApiResponse<T>(Result result, T data) {
    public record Result(int code, boolean success, String message) {}
    public static <T> ResponseEntity<ApiResponse<T>> success(SuccessCode code, T data) {
        return ResponseEntity.status(code.getStatus()).body(new ApiResponse<>(
                new Result(code.getStatus().value(), true, code.getMessage()), data));
    }
    public static ResponseEntity<ApiResponse<Void>> success(SuccessCode code) {
        return success(code, null);
    }
    public static ResponseEntity<ApiResponse<Void>> error(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(errorBody(code));
    }
    /** Security 필터에서도 MVC와 동일한 본문을 직렬화한다. */
    public static ApiResponse<Void> errorBody(ErrorCode code) {
        return new ApiResponse<>(new Result(code.getStatus().value(), false, code.getMessage()), null);
    }
}
