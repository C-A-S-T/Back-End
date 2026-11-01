package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.domain.enums.ClaimStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(
        name = "claim",
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

    @Column(name = "claim_content", nullable = false,
            columnDefinition = "text")
    private String claimContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "claim_status", nullable = false, length = 20)
    private ClaimStatus claimStatus = ClaimStatus.DRAFT;

    public Claim(Answer answer, Integer claimIndex, String claimContent) {
        this.answer = Objects.requireNonNull(
                answer, "answer is required"
        );
        this.claimIndex = Objects.requireNonNull(
                claimIndex, "claimIndex is required"
        );
        this.claimContent = Objects.requireNonNull(
                claimContent, "claimContent is required"
        );

        if (claimIndex < 0) {
            throw new IllegalArgumentException(
                    "claimIndex must be zero or greater"
            );
        }
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
