package com.example.cast.domain.entity;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Getter
@Entity
@Table(name = "research_query")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchQuery extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "query_id")
    private Long queryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "research_title", columnDefinition = "text")
    private String researchTitle;

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question;

    public ResearchQuery(User user, String question) {
        this.user = Objects.requireNonNull(user, "user is required");
        this.question = Objects.requireNonNull(
                question, "question is required"
        );
    }

    public void updateResearchTitle(String researchTitle) {
        this.researchTitle = researchTitle;
    }
}
