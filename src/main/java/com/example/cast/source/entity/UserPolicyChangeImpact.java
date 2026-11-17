package com.example.cast.source.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Per-user impact. NULL requiresRecheck means unknown, not false. */
@Getter
@Entity
@Table(schema = "backend", name = "user_policy_change_impact")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserPolicyChangeImpact extends BaseTimeEntity {
    @EmbeddedId
    private ImpactId id;

    @MapsId("userId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @MapsId("policyChangeId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "policy_change_id", nullable = false)
    private PolicyChange policyChange;

    @Column(name = "requires_recheck")
    private Boolean requiresRecheck;

    @Column(name = "impact_summary", columnDefinition = "text")
    private String impactSummary;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @Embeddable
    @Getter
    @EqualsAndHashCode
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    @AllArgsConstructor
    public static class ImpactId implements Serializable {
        private static final long serialVersionUID = 1L;

        @Column(name = "user_id", nullable = false)
        private Long userId;

        @Column(name = "policy_change_id", nullable = false)
        private Long policyChangeId;
    }
}
