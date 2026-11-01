package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;
import java.util.Objects;

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
            nullable = false,
            columnDefinition = "text"
    )
    private String question;

    @Column(name = "reference_answer", columnDefinition = "text")
    private String referenceAnswer;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "expected_claims",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private List<String> expectedClaims = List.of();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(
            name = "relevant_chunk_ids",
            nullable = false,
            columnDefinition = "jsonb"
    )
    private List<Long> relevantChunkIds = List.of();

    @Column(name = "category", length = 100)
    private String category;

    public EvaluationCase(
            String question,
            String referenceAnswer,
            List<String> expectedClaims,
            List<Long> relevantChunkIds,
            String category
    ) {
        this.question = Objects.requireNonNull(
                question, "question is required"
        );

        if (question.isBlank()) {
            throw new IllegalArgumentException(
                    "question must not be blank"
            );
        }

        this.expectedClaims = List.copyOf(
                Objects.requireNonNull(
                        expectedClaims, "expectedClaims is required"
                )
        );

        this.relevantChunkIds = List.copyOf(
                Objects.requireNonNull(
                        relevantChunkIds, "relevantChunkIds is required"
                )
        );

        if (category != null && category.length() > 100) {
            throw new IllegalArgumentException(
                    "category must not exceed 100 characters"
            );
        }

        this.referenceAnswer = referenceAnswer;
        this.category = category;
    }
}
