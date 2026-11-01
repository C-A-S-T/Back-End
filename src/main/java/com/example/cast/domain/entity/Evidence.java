package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.domain.enums.EvidenceRelation;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(name = "evidence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "evidence_id")
    private Long evidenceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "verification_id",
            nullable = false,
            updatable = false
    )
    private Verification verification;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chunk_id",
            nullable = false,
            updatable = false
    )
    private DocumentChunk chunk;

    @Column(
            name = "evidence_text",
            nullable = false,
            columnDefinition = "text"
    )
    private String evidenceText;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "evidence_relation",
            nullable = false,
            length = 20
    )
    private EvidenceRelation evidenceRelation;

    public Evidence(
            Verification verification,
            DocumentChunk chunk,
            String evidenceText,
            EvidenceRelation evidenceRelation
    ) {
        this.verification = Objects.requireNonNull(
                verification, "verification is required"
        );

        this.chunk = Objects.requireNonNull(
                chunk, "document chunk is required"
        );

        this.evidenceText = Objects.requireNonNull(
                evidenceText, "evidenceText is required"
        );

        if (evidenceText.isBlank()) {
            throw new IllegalArgumentException(
                    "evidenceText must not be blank"
            );
        }

        this.evidenceRelation = Objects.requireNonNull(
                evidenceRelation, "evidenceRelation is required"
        );
    }
}
