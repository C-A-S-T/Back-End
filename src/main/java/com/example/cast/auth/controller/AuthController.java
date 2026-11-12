package com.example.cast.auth.controller;

import com.example.cast.auth.code.AuthSuccessCode;
import com.example.cast.auth.dto.request.LoginRequest;
import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.auth.service.AuthService;
import com.example.cast.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** 인증 API는 가입과 로그인 두 경로만 제공한다. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<AuthResponse>> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(AuthSuccessCode.SIGNUP_SUCCESS, auth.signup(request));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(AuthSuccessCode.LOGIN_SUCCESS, auth.login(request));
    }
}
