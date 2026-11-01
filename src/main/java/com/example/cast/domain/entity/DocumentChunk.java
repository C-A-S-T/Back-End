package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(
        name = "document_chunk",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_document_chunk_index",
                columnNames = {"document_id", "chunk_index"}
        )
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocumentChunk extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chunk_id")
    private Long chunkId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(name = "chunk_content", nullable = false,
            columnDefinition = "text")
    private String chunkContent;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "section_title", columnDefinition = "text")
    private String sectionTitle;

    public DocumentChunk(
            Document document,
            Integer chunkIndex,
            String chunkContent
    ) {
        this.document = Objects.requireNonNull(document);
        this.chunkIndex = Objects.requireNonNull(chunkIndex);
        this.chunkContent = Objects.requireNonNull(chunkContent);

        if (chunkIndex < 0) {
            throw new IllegalArgumentException(
                    "chunkIndex must be zero or greater"
            );
        }
    }

    public void updatePageRange(Integer pageStart, Integer pageEnd) {
        if (pageStart != null && pageStart < 1) {
            throw new IllegalArgumentException(
                    "pageStart must be positive"
            );
        }
        if (pageEnd != null && pageEnd < 1) {
            throw new IllegalArgumentException(
                    "pageEnd must be positive"
            );
        }
        if (pageStart != null && pageEnd != null
                && pageEnd < pageStart) {
            throw new IllegalArgumentException(
                    "pageEnd must not be before pageStart"
            );
        }

        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
    }

    public void updateSectionTitle(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }
}
