package com.example.cast.research.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Getter
@Entity
@Table(schema = "backend", name = "research_task")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchTask extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long taskId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "query_id", nullable = false, updatable = false)
    private ResearchQuery query;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResearchTaskStatus status = ResearchTaskStatus.PENDING;

    @Column(name = "current_step")
    private Short currentStep;

    @Column(name = "preview_summary")
    private String previewSummary;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "preview_updated_at")
    private OffsetDateTime previewUpdatedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP_WITH_TIMEZONE)
    @Column(name = "canceled_at")
    private OffsetDateTime canceledAt;

    @Column(name = "error_message")
    private String errorMessage;

    public ResearchTask(ResearchQuery query) {
        this.query = query;
    }

    public void start() {
        if (status != ResearchTaskStatus.PENDING) {
            throw new IllegalStateException(
                    "Only a pending research task can start"
            );
        }

        this.status = ResearchTaskStatus.RUNNING;
        this.startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void updateProgress(Short currentStep, String previewSummary) {
        if (status != ResearchTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only a running task can update progress"
            );
        }

        this.currentStep = currentStep;
        this.previewSummary = previewSummary;
        this.previewUpdatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markCompleted() {
        if (status != ResearchTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Only a running task can be completed"
            );
        }

        this.status = ResearchTaskStatus.COMPLETED;
        this.completedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markCanceled() {
        if (status != ResearchTaskStatus.PENDING
                && status != ResearchTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Completed or terminated tasks cannot be canceled"
            );
        }

        this.status = ResearchTaskStatus.CANCELED;
        this.canceledAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void markFailed(String errorMessage) {
        if (status != ResearchTaskStatus.PENDING
                && status != ResearchTaskStatus.RUNNING) {
            throw new IllegalStateException(
                    "Completed or terminated tasks cannot fail again"
            );
        }

        this.status = ResearchTaskStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
