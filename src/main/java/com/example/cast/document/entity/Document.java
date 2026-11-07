package com.example.cast.document.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "doc_title", nullable = false)
    private String docTitle;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "file_storage_path")
    private String fileStoragePath;

    @Column(name = "published_at")
    private LocalDate publishedAt;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "doc_version", length = 100)
    private String docVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType = SourceType.OTHER;

    @Enumerated(EnumType.STRING)
    @Column(name = "reliability_level", length = 20)
    private ReliabilityLevel reliabilityLevel;

    @Column(name = "collected_at", nullable = false)
    private java.time.Instant collectedAt;

    @Column(name = "content_hash", length = 64, updatable = false)
    private String contentHash;

    /** 수집 시각은 호출자가 명시한다. 레거시 문서는 해시가 없을 수 있다. */
    public Document(String docTitle, String contentHash, java.time.Instant collectedAt) {
        this.docTitle = docTitle;
        this.contentHash = contentHash;
        this.collectedAt = collectedAt;
    }

    public void updateSourceInformation(
            String sourceName,
            String sourceUrl,
            String fileStoragePath,
            String docVersion,
            SourceType sourceType
    ) {
        this.sourceName = sourceName;
        this.sourceUrl = sourceUrl;
        this.fileStoragePath = fileStoragePath;
        this.docVersion = docVersion;
        this.sourceType = sourceType;
    }

    public void updateValidity(
            LocalDate publishedAt,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        this.publishedAt = publishedAt;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public void updateReliabilityLevel(ReliabilityLevel reliabilityLevel) {
        this.reliabilityLevel = reliabilityLevel;
    }

    public void updateTitle(String docTitle) {
        this.docTitle = docTitle;
    }
}
