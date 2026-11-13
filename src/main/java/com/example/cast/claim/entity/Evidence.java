package com.example.cast.claim.entity;
import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.document.entity.Document;
import jakarta.persistence.*;
import lombok.*;
/** 인용 문구와 위치를 보존한다. AI 청크가 바뀌어도 사용자 근거는 유지한다. */
@Getter
@Entity
@Table(schema = "backend", name = "evidence")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Evidence extends BaseTimeEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long evidenceId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "verification_id", nullable = false, updatable = false)
    private Verification verification;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, updatable = false)
    private Document document;
    private Long aiChunkId;
    @Column(nullable = false)
    private String evidenceText;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private EvidenceRelation evidenceRelation;
    private Integer pageStart;
    private Integer pageEnd;
    private String sectionTitle;
    public Evidence(Verification verification, Document document, Long aiChunkId, String text,
            EvidenceRelation relation, Integer pageStart, Integer pageEnd, String sectionTitle) {
        this.verification = verification; this.document = document; this.aiChunkId = aiChunkId;
        this.evidenceText = text; this.evidenceRelation = relation;
        this.pageStart = pageStart; this.pageEnd = pageEnd; this.sectionTitle = sectionTitle;
    }
}
