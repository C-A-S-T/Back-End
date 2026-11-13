package com.example.cast.auth.service;

import com.example.cast.auth.code.AuthErrorCode;
import com.example.cast.auth.dto.request.LoginRequest;
import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import com.example.cast.user.dto.request.UserInputs;
import com.example.cast.user.entity.AccountStatus;
import com.example.cast.user.entity.User;
import com.example.cast.user.repository.UserRepository;
import com.example.cast.user.service.UserWriteService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 정책을 담당하며 실제 회원 생성은 기존 쓰기 서비스에 위임한다. */
@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository users;
    private final UserWriteService userWriter;
    private final RequestValidator validator;
    private final PasswordEncoder passwords;
    private final JwtTokenService tokens;
    private final String dummyPasswordHash;

    public AuthService(UserRepository users, UserWriteService userWriter, RequestValidator validator,
                       PasswordEncoder passwords, JwtTokenService tokens) {
        this.users = users;
        this.userWriter = userWriter;
        this.validator = validator;
        this.passwords = passwords;
        this.tokens = tokens;
        // 없는 이메일도 BCrypt 검증을 거쳐 계정 존재에 따른 응답 시간 차이를 줄인다.
        this.dummyPasswordHash = passwords.encode(UUID.randomUUID().toString());
    }

    /** 저장/flush가 성공해야 토큰을 발급하며, 응답 전달 전 트랜잭션이 커밋된다. */
    @Transactional
    public AuthResponse signup(SignupRequest request) {
        validator.validate(request);
        String email = normalizeEmail(request.email());
        if (users.existsByEmailIgnoreCase(email)) throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
        validatePasswordBytes(request.password());
        User user;
        try {
            user = userWriter.create(new UserInputs.Create(email, passwords.encode(request.password())));
            users.flush();
        } catch (RuntimeException exception) {
            // IDENTITY 삽입은 create 안에서 즉시 실행될 수 있으므로 저장과 flush 모두 보호한다.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof ConstraintViolationException violation
                        && "23505".equals(violation.getSQLState())
                        && "app_user_email_key".equals(violation.getConstraintName())) {
                    throw new BusinessException(AuthErrorCode.EMAIL_ALREADY_EXISTS);
                }
            }
            // 다른 제약 위반은 이메일 오류로 오인하지 않고, DB 행의 해시가 로그로 유출되지 않게 한다.
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof DataIntegrityViolationException || cause instanceof ConstraintViolationException) {
                    throw new BusinessException(GlobalErrorCode.DATA_CONFLICT);
                }
            }
            throw exception;
        }
        return tokens.issue(user);
    }

    /** 비밀번호 검증에 성공한 사용자에게만 계정 상태를 알린다. */
    public AuthResponse login(LoginRequest request) {
        validator.validate(request);
        String email = normalizeEmail(request.email());
        validatePasswordBytes(request.password());
        var candidates = users.findAllByEmailIgnoreCase(email);
        User user = candidates.size() == 1 ? candidates.getFirst() : null;
        boolean matches = passwords.matches(request.password(), user == null ? dummyPasswordHash : user.getPasswordHash());
        if (user == null || !matches) throw new BusinessException(AuthErrorCode.INVALID_CREDENTIALS);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) throw new BusinessException(AuthErrorCode.ACCOUNT_DISABLED);
        return tokens.issue(user);
    }

    private String normalizeEmail(String rawEmail) {
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        return validator.validate(new NormalizedEmail(email)).value();
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
    }

    /** 정규화 이후 검증하므로 정상 이메일 앞뒤 공백은 허용한다. */
    private record NormalizedEmail(@NotBlank @Email @Size(max = 320) String value) {}
}
