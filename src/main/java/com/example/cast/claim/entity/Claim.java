package com.example.cast.claim.entity;

import com.example.cast.research.entity.Answer;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(schema = "backend", name = "claim",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_claim_answer_index",
                columnNames = {"answer_id", "claim_index"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Claim extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "claim_id")
    private Long claimId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "answer_id", nullable = false, updatable = false)
    private Answer answer;

    @Column(name = "claim_index", nullable = false, updatable = false)
    private Integer claimIndex;

    @Column(name = "claim_content", nullable = false)
    private String claimContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false, length = 20)
    private ClaimStatus claimStatus = ClaimStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_type", length = 20)
    private ClaimType claimType;

    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "claim_value")
    private java.util.Map<String, Object> claimValue;

    /** 서비스가 검증 이력 확인 및 Claim 잠금을 획득한 뒤에만 호출한다. */
    public void revise(String content, ClaimType type, java.util.Map<String, Object> value) {
        this.claimContent = content;
        this.claimType = type;
        this.claimValue = java.util.Map.copyOf(value);
    }

    public Claim(Answer answer, Integer claimIndex, String claimContent) {
        this.answer = answer;
        this.claimIndex = claimIndex;
        this.claimContent = claimContent;
    }

    public void confirm() {
        if (claimStatus != ClaimStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only a draft claim can be confirmed"
            );
        }

        this.claimStatus = ClaimStatus.CONFIRMED;
    }

    public void discard() {
        if (claimStatus == ClaimStatus.DISCARDED) {
            throw new IllegalStateException(
                    "Claim is already discarded"
            );
        }

        this.claimStatus = ClaimStatus.DISCARDED;
    }
}
