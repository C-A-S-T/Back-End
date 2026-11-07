package com.example.cast.claim.entity;

import com.example.cast.document.entity.DocumentChunk;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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
            nullable = false
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
        this.verification = verification;

        this.chunk = chunk;

        this.evidenceText = evidenceText;

        this.evidenceRelation = evidenceRelation;
    }
}
