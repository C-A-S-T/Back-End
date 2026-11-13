package com.example.cast.research.entity;

import com.example.cast.user.entity.User;

import com.example.cast.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(schema = "backend", name = "research_query")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ResearchQuery extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "query_id")
    private Long queryId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "research_title")
    private String researchTitle;

    @Column(name = "question", nullable = false)
    private String question;

    public ResearchQuery(User user, String question) {
        this.user = user;
        this.question = question;
    }

    public void updateResearchTitle(String researchTitle) {
        this.researchTitle = researchTitle;
    }
}
