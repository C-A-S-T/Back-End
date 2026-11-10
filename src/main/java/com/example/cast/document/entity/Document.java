package com.example.cast.document.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.document.dto.SourceSnapshot;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

/** 사용자 조회/저장용 출처 스냅샷. AI 원문 테이블과 생명주기를 공유하지 않는다. */
@Getter
@Entity
@Table(schema = "backend", name = "document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Document extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long documentId;
    @Column(nullable = false, unique = true, updatable = false)
    private Long aiDocumentId;
    @Column(nullable = false)
    private String docTitle;
    @Column(length = 255)
    private String sourceName;
    private String sourceUrl;
    private LocalDate publishedAt;
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;
    @Column(length = 100)
    private String docVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private SourceType sourceType;
    @Enumerated(EnumType.STRING) @Column(length = 20)
    private ReliabilityLevel reliabilityLevel;

    /** 원문 식별자는 AI가 발급하고, 원문 내용/버전 변경 시 새 식별자를 발급해야 한다. */
    public Document(SourceSnapshot source) {
        this.aiDocumentId = source.aiDocumentId();
        this.docTitle = source.title();
        this.sourceName = source.sourceName();
        this.sourceUrl = source.sourceUrl();
        this.publishedAt = source.publishedAt();
        this.effectiveFrom = source.effectiveFrom();
        this.effectiveTo = source.effectiveTo();
        this.docVersion = source.version();
        this.sourceType = source.sourceType();
        this.reliabilityLevel = source.reliabilityLevel();
    }
}
