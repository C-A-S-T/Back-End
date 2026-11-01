package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.domain.enums.VerificationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(
        name = "verification",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_verification_claim_attempt",
                columnNames = {"claim_id", "ver_attempt_number"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Verification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "verification_id")
    private Long verificationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private VerificationStatus status = VerificationStatus.IN_PROGRESS;

    @Column(name = "verification_reason", columnDefinition = "text")
    private String verificationReason;

    @Column(
            name = "ver_attempt_number",
            nullable = false,
            updatable = false
    )
    private Integer verAttemptNumber;

    public Verification(Claim claim, Integer verAttemptNumber) {
        this.claim = Objects.requireNonNull(
                claim, "claim is required"
        );

        this.verAttemptNumber = Objects.requireNonNull(
                verAttemptNumber, "verAttemptNumber is required"
        );

        if (verAttemptNumber < 1) {
            throw new IllegalArgumentException(
                    "verAttemptNumber must be 1 or greater"
            );
        }
    }

    public void complete(
            VerificationStatus resultStatus,
            String verificationReason
    ) {
        Objects.requireNonNull(
                resultStatus, "verification result is required"
        );

        if (status != VerificationStatus.IN_PROGRESS) {
            throw new IllegalStateException(
                    "Verification is already completed"
            );
        }

        if (resultStatus == VerificationStatus.IN_PROGRESS) {
            throw new IllegalArgumentException(
                    "IN_PROGRESS is not a completed result"
            );
        }

        this.status = resultStatus;
        this.verificationReason = verificationReason;
    }

    public boolean isCompleted() {
        return status != VerificationStatus.IN_PROGRESS;
    }
}
