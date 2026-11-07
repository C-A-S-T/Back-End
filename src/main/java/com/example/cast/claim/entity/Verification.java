package com.example.cast.claim.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "verification_reason")
    private String verificationReason;

    @Column(
            name = "ver_attempt_number",
            nullable = false,
            updatable = false
    )
    private Integer verAttemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "verification_method", length = 20)
    private VerificationMethod verificationMethod;
    @Enumerated(EnumType.STRING)
    @Column(name = "rule_result", length = 24)
    private VerificationStatus ruleResult;
    @Enumerated(EnumType.STRING)
    @Column(name = "llm_result", length = 24)
    private VerificationStatus llmResult;
    private Double confidence;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id")
    private com.example.cast.retrieval.entity.RetrievalRun retrievalRun;

    /** 검색 실행의 소속을 서비스에서 확인한 후 검증 시작 시 연결한다. */
    public void linkRun(com.example.cast.retrieval.entity.RetrievalRun run) {
        if (isCompleted()) throw new IllegalStateException("Verification is already completed");
        this.retrievalRun = run;
    }

    /** 서비스에서 결과 조합과 신뢰도를 검증한 뒤 한 번만 완료한다. */
    public void complete(VerificationStatus result, String reason, VerificationMethod method,
            VerificationStatus rule, VerificationStatus llm, Double confidence) {
        complete(result, reason);
        this.verificationMethod = method;
        this.ruleResult = rule;
        this.llmResult = llm;
        this.confidence = confidence;
    }

    public Verification(Claim claim, Integer verAttemptNumber) {
        this.claim = claim;

        this.verAttemptNumber = verAttemptNumber;

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
