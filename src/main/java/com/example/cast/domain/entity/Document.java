package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.domain.enums.ReliabilityLevel;
import com.example.cast.domain.enums.SourceType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Getter
@Entity
@Table(name = "document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long documentId;

    @Column(name = "doc_title", nullable = false,
            columnDefinition = "text")
    private String docTitle;

    @Column(name = "source_name", length = 255)
    private String sourceName;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "file_storage_path", columnDefinition = "text")
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

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "collected_at", nullable = false,
            columnDefinition = "timestamptz")
    private OffsetDateTime collectedAt =
            OffsetDateTime.now(ZoneOffset.UTC);

    public Document(String docTitle) {
        this.docTitle = Objects.requireNonNull(docTitle);
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
        this.sourceType = Objects.requireNonNull(sourceType);
    }

    public void updateValidity(
            LocalDate publishedAt,
            LocalDate effectiveFrom,
            LocalDate effectiveTo
    ) {
        if (effectiveFrom != null && effectiveTo != null
                && effectiveTo.isBefore(effectiveFrom)) {
            throw new IllegalArgumentException(
                    "effectiveTo must not be before effectiveFrom"
            );
        }

        this.publishedAt = publishedAt;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
    }

    public void updateReliabilityLevel(ReliabilityLevel reliabilityLevel) {
        this.reliabilityLevel = reliabilityLevel;
    }

    public void updateTitle(String docTitle) {
        this.docTitle = Objects.requireNonNull(docTitle);
    }
}
