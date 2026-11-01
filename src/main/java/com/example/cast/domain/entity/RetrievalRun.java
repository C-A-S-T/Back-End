package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.domain.enums.RetrievalRunType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(name = "retrieval_run")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetrievalRun extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "run_id")
    private Long runId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, updatable = false)
    private ResearchTask task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", updatable = false)
    private Claim claim;

    @Column(
            name = "re_attempt_number",
            nullable = false,
            updatable = false
    )
    private Integer reAttemptNumber = 0;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "run_type",
            nullable = false,
            length = 24,
            updatable = false
    )
    private RetrievalRunType runType;

    @Column(
            name = "search_query",
            nullable = false,
            updatable = false,
            columnDefinition = "text"
    )
    private String searchQuery;

    private RetrievalRun(
            ResearchTask task,
            Claim claim,
            RetrievalRunType runType,
            Integer reAttemptNumber,
            String searchQuery
    ) {
        this.task = Objects.requireNonNull(
                task, "research task is required"
        );

        this.claim = claim;

        this.runType = Objects.requireNonNull(
                runType, "runType is required"
        );

        this.reAttemptNumber = Objects.requireNonNull(
                reAttemptNumber, "reAttemptNumber is required"
        );

        this.searchQuery = Objects.requireNonNull(
                searchQuery, "searchQuery is required"
        );

        if (searchQuery.isBlank()) {
            throw new IllegalArgumentException(
                    "searchQuery must not be blank"
            );
        }
    }

    public static RetrievalRun initial(
            ResearchTask task,
            String searchQuery
    ) {
        return new RetrievalRun(
                task,
                null,
                RetrievalRunType.INITIAL,
                0,
                searchQuery
        );
    }

    public static RetrievalRun claimSearch(
            ResearchTask task,
            Claim claim,
            String searchQuery
    ) {
        Objects.requireNonNull(
                claim, "claim is required"
        );

        return new RetrievalRun(
                task,
                claim,
                RetrievalRunType.CLAIM_SEARCH,
                0,
                searchQuery
        );
    }

    public static RetrievalRun retry(
            ResearchTask task,
            Claim claim,
            Integer attemptNumber,
            String searchQuery
    ) {
        Objects.requireNonNull(
                claim, "claim is required"
        );

        Objects.requireNonNull(
                attemptNumber, "attemptNumber is required"
        );

        if (attemptNumber < 1 || attemptNumber > 3) {
            throw new IllegalArgumentException(
                    "Retry number must be between 1 and 3"
            );
        }

        return new RetrievalRun(
                task,
                claim,
                RetrievalRunType.RETRY,
                attemptNumber,
                searchQuery
        );
    }
}
