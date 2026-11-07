package com.example.cast.evaluation.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Getter
@Entity
@Table(name = "evaluation_case")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EvaluationCase extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "eval_case_id")
    private Long evalCaseId;

    @Column(
            name = "question",
            nullable = false
    )
    private String question;

    @Column(name = "reference_answer")
    private String referenceAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "expected_claims",
            nullable = false
    )
    private List<ExpectedClaim> expectedClaims = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "relevant_chunk_ids",
            nullable = false
    )
    private List<Long> relevantChunkIds = List.of();

    @Column(name = "category", length = 100)
    private String category;

    public EvaluationCase(
            String question,
            String referenceAnswer,
            List<ExpectedClaim> expectedClaims,
            List<Long> relevantChunkIds,
            String category
    ) {
        this.question = question;

        this.expectedClaims = List.copyOf(
                expectedClaims
        );

        this.relevantChunkIds = List.copyOf(
                relevantChunkIds
        );

        this.referenceAnswer = referenceAnswer;
        this.category = category;
    }
}
