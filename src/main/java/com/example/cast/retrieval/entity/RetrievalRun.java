package com.example.cast.retrieval.entity;

import com.example.cast.research.entity.ResearchTask;
import com.example.cast.claim.entity.Claim;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
            updatable = false
    )
    private String searchQuery;

    private RetrievalRun(
            ResearchTask task,
            Claim claim,
            RetrievalRunType runType,
            Integer reAttemptNumber,
            String searchQuery
    ) {
        this.task = task;

        this.claim = claim;

        this.runType = runType;

        this.reAttemptNumber = reAttemptNumber;

        this.searchQuery = searchQuery;
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
