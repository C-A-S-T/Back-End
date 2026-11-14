package com.example.cast.source.entity;

import com.example.cast.claim.entity.Evidence;
import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 사용자별 Evidence 북마크. 근거 본문과 문서 데이터는 복제하지 않는다. */
@Getter
@Entity
@Table(schema = "backend", name = "sentence_scrap",
        uniqueConstraints = @UniqueConstraint(name = "uq_sentence_scrap_user_evidence",
                columnNames = {"user_id", "evidence_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SentenceScrap extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scrap_id")
    private Long scrapId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "evidence_id", nullable = false, updatable = false)
    private Evidence evidence;

    public SentenceScrap(User user, Evidence evidence) {
        this.user = user;
        this.evidence = evidence;
    }
}
