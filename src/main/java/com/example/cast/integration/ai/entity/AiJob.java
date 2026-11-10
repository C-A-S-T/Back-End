package com.example.cast.integration.ai.entity;
import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.research.entity.ResearchTask;
import com.example.cast.integration.ai.dto.AiJobRequest;
import com.example.cast.integration.ai.dto.AiResultRequest.Outcome;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
/** AI 모델 데이터가 아닌 백엔드의 전송 대기/결과 수신 기록. */
@Getter
@Entity
@Table(schema = "backend", name = "ai_job")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiJob extends BaseTimeEntity {
    @Id private UUID jobId;
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, unique = true, updatable = false)
    private ResearchTask task;
    @Column(nullable = false, updatable = false)
    private String question;
    private Instant dispatchedAt;
    @Column(length = 64)
    private String resultHash;
    @Enumerated(EnumType.STRING) @Column(length = 12)
    private Outcome outcome;
    public AiJob(ResearchTask task) {
        this.jobId = UUID.randomUUID(); this.task = task; this.question = task.getQuery().getQuestion();
    }
    public AiJobRequest request() { return new AiJobRequest(jobId, task.getTaskId(), question); }
    public void markDispatched(Instant time) { if (dispatchedAt == null) dispatchedAt = time; }
    public void accept(String hash, Outcome outcome) { this.resultHash = hash; this.outcome = outcome; }
}
