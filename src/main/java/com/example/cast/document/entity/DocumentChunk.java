package com.example.cast.document.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Column(name = "chunk_content", nullable = false)
    private String chunkContent;

    @Column(name = "page_start")
    private Integer pageStart;

    @Column(name = "page_end")
    private Integer pageEnd;

    @Column(name = "section_title")
    private String sectionTitle;

    public DocumentChunk(
            Document document,
            Integer chunkIndex,
            String chunkContent
    ) {
        this.document = document;
        this.chunkIndex = chunkIndex;
        this.chunkContent = chunkContent;
    }

    public void updatePageRange(Integer pageStart, Integer pageEnd) {
        this.pageStart = pageStart;
        this.pageEnd = pageEnd;
    }

    public void updateSectionTitle(String sectionTitle) {
        this.sectionTitle = sectionTitle;
    }
}
