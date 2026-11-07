package com.example.cast.user.dto.request;
import com.example.cast.user.entity.*;
import jakarta.validation.constraints.*;
/** 서비스 경계의 입력 계약. 엔티티 생성/변경 전에 검증한다. */
public final class UserInputs {
    private UserInputs() {}
    public record Create(@NotBlank @Email @Size(max=320) String email, @NotBlank String passwordHash) {}
    public record Password(@NotBlank String hash) {}
    public record Status(@NotNull AccountStatus status) {}
    public record Consent(@NotNull ConsentType type, @Size(max=100) String version,
            boolean agreed, @NotNull java.time.OffsetDateTime occurredAt) {}
}
