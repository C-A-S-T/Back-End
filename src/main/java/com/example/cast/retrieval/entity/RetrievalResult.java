package com.example.cast.retrieval.entity;

import com.example.cast.document.entity.DocumentChunk;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
        name = "retrieval_result",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_retrieval_result_rank",
                        columnNames = {"run_id", "rank"}
                ),
                @UniqueConstraint(
                        name = "uq_retrieval_result_chunk",
                        columnNames = {"run_id", "chunk_id"}
                )
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RetrievalResult extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "run_id",
            nullable = false,
            updatable = false
    )
    private RetrievalRun run;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chunk_id",
            nullable = false,
            updatable = false
    )
    private DocumentChunk chunk;

    @Column(name = "rank", nullable = false, updatable = false)
    private Integer rank;

    @Column(
            name = "score",
            updatable = false
    )
    private Double score;

    public RetrievalResult(
            RetrievalRun run,
            DocumentChunk chunk,
            Integer rank,
            Double score
    ) {
        this.run = run;

        this.chunk = chunk;

        this.rank = rank;

        this.score = score;
    }
}
