package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import com.example.cast.domain.enums.AnswerStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(name = "answer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Answer extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id")
    private Long answerId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "task_id",
            nullable = false,
            unique = true,
            updatable = false
    )
    private ResearchTask task;

    @Column(name = "answer_content", columnDefinition = "text")
    private String answerContent;

    @Column(name = "summary_content", columnDefinition = "text")
    private String summaryContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_status", nullable = false, length = 20)
    private AnswerStatus answerStatus = AnswerStatus.DRAFT;

    public Answer(ResearchTask task) {
        this.task = Objects.requireNonNull(
                task, "research task is required"
        );
    }

    public void markFinal(
            String answerContent,
            String summaryContent
    ) {
        if (answerStatus != AnswerStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only a draft answer can become final"
            );
        }

        if (answerContent == null || answerContent.isBlank()) {
            throw new IllegalArgumentException(
                    "Final answer content is required"
            );
        }

        this.answerContent = answerContent;
        this.summaryContent = summaryContent;
        this.answerStatus = AnswerStatus.FINAL;
    }

    public void abort() {
        if (answerStatus != AnswerStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only a draft answer can be aborted"
            );
        }

        this.answerStatus = AnswerStatus.ABORTED;
    }
}
