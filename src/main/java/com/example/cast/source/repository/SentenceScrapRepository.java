package com.example.cast.source.repository;

import com.example.cast.source.entity.SentenceScrap;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SentenceScrapRepository extends JpaRepository<SentenceScrap, Long> {
    Optional<SentenceScrap> findByScrapIdAndUser_UserId(Long scrapId, Long userId);
    Optional<SentenceScrap> findByUser_UserIdAndEvidence_EvidenceId(Long userId, Long evidenceId);
    List<SentenceScrap> findByUser_UserIdAndEvidence_EvidenceIdIn(
            Long userId, Collection<Long> evidenceIds);
    long countByUser_UserId(Long userId);
}
