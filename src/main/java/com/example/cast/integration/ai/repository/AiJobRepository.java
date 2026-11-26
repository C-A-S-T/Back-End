package com.example.cast.integration.ai.repository;
import com.example.cast.integration.ai.entity.AiJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;
/** 전송은 적어도 한 번 수행하며 중복 제거 키는 jobId다. */
public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
    Optional<AiJob> findByTaskTaskId(Long taskId);

    /** 보호 대상과 전환 시각 이전 작업은 LIMIT 적용 이전에 DB에서 제외한다. */
    @Query("select j from AiJob j join fetch j.task t "
            + "where j.dispatchedAt is null and j.resultHash is null "
            + "and t.status = com.example.cast.research.entity.ResearchTaskStatus.RUNNING "
            + "and j.createdAt > :cutoverAt and j.jobId not in :protectedJobIds "
            + "order by j.createdAt, j.jobId")
    List<AiJob> pendingEligible(@Param("cutoverAt") Instant cutoverAt,
            @Param("protectedJobIds") Collection<UUID> protectedJobIds, Pageable pageable);
}
