package com.example.cast.source.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.document.entity.Document;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A confirmed change between two known snapshots of the same policy. */
@Getter
@Entity
@Table(schema = "backend", name = "policy_change",
        uniqueConstraints = @UniqueConstraint(name = "uq_policy_change_comparison",
                columnNames = {"policy_key", "previous_document_id", "current_document_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PolicyChange extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "policy_change_id")
    private Long policyChangeId;

    @Column(name = "policy_key", nullable = false, length = 160)
    private String policyKey;

    @Column(name = "policy_title", nullable = false, columnDefinition = "text")
    private String policyTitle;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private SourceFolderCategory category;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "previous_document_id", nullable = false)
    private Document previousDocument;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_document_id", nullable = false)
    private Document currentDocument;

    @Column(name = "change_summary", nullable = false, columnDefinition = "text")
    private String changeSummary;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @OneToMany(mappedBy = "policyChange")
    private List<UserPolicyChangeImpact> impacts = new ArrayList<>();
}
