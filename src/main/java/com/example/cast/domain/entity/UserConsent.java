package com.example.cast.domain.entity;

import com.example.cast.domain.enums.ConsentType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

@Getter
@Entity
@Table(name = "user_consent")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserConsent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "consent_id")
    private Long consentId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_type", nullable = false,
            length = 40, updatable = false)
    private ConsentType consentType;

    @Column(name = "policy_version", length = 100, updatable = false)
    private String policyVersion;

    @Column(name = "is_agreed", nullable = false, updatable = false)
    private boolean isAgreed;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "occurred_at", nullable = false,
            updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime occurredAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "created_at", nullable = false,
            updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    public UserConsent(
            User user,
            ConsentType consentType,
            String policyVersion,
            boolean isAgreed,
            OffsetDateTime occurredAt
    ) {
        this.user = Objects.requireNonNull(user);
        this.consentType = Objects.requireNonNull(consentType);
        this.policyVersion = policyVersion;
        this.isAgreed = isAgreed;
        this.occurredAt = Objects.requireNonNull(occurredAt);
    }

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        }
    }
}
