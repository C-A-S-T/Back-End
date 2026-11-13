package com.example.cast.auth;

import com.example.cast.auth.code.AuthErrorCode;
import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.service.AuthService;
import com.example.cast.auth.service.JwtTokenService;
import com.example.cast.global.code.ErrorCode;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import com.example.cast.user.repository.UserRepository;
import com.example.cast.user.service.UserWriteService;
import jakarta.validation.Validation;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 사전 조회를 통과한 뒤 발생하는 DB 충돌의 분류와 토큰 미발급을 결정적으로 검증한다. */
class AuthServiceConflictTest {
    @Test void emailUniqueViolationDuringIdentityInsertIsEmailConflict() {
        verifyConflict("23505", "app_user_email_key", false, AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test void emailUniqueViolationDuringFlushIsEmailConflict() {
        verifyConflict("23505", "app_user_email_key", true, AuthErrorCode.EMAIL_ALREADY_EXISTS);
    }

    @Test void otherUniqueConstraintIsNotReportedAsDuplicateEmail() {
        verifyConflict("23505", "another_unique_constraint", false, GlobalErrorCode.DATA_CONFLICT);
    }

    @Test void nonUniqueIntegrityViolationIsNotReportedAsDuplicateEmail() {
        verifyConflict("23514", "app_user_email_key", true, GlobalErrorCode.DATA_CONFLICT);
    }

    private void verifyConflict(String sqlState, String constraint, boolean duringFlush, ErrorCode expected) {
        var users = mock(UserRepository.class);
        var writer = mock(UserWriteService.class);
        var passwords = mock(PasswordEncoder.class);
        var tokens = mock(JwtTokenService.class);
        when(passwords.encode(anyString())).thenReturn("test-hash");
        var violation = mock(ConstraintViolationException.class);
        when(violation.getSQLState()).thenReturn(sqlState);
        when(violation.getConstraintName()).thenReturn(constraint);
        var exception = new DataIntegrityViolationException("test-only-database-failure", violation);
        if (duringFlush) doThrow(exception).when(users).flush();
        else when(writer.create(any())).thenThrow(exception);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var service = new AuthService(users, writer, new RequestValidator(factory.getValidator()), passwords, tokens);
            assertThatThrownBy(() -> service.signup(new SignupRequest("user@example.com", "Password123!")))
                    .isInstanceOfSatisfying(BusinessException.class, error ->
                            org.assertj.core.api.Assertions.assertThat(error.getErrorCode()).isEqualTo(expected));
            verifyNoInteractions(tokens);
        }
    }
}
