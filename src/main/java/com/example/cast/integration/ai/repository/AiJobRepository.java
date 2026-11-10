package com.example.cast.integration.ai.repository;
import com.example.cast.integration.ai.entity.AiJob;
import org.springframework.data.jpa.repository.*;
import java.util.*;
/** 전송은 적어도 한 번 수행하며 중복 제거 키는 jobId다. */
public interface AiJobRepository extends JpaRepository<AiJob, UUID> {
    Optional<AiJob> findByTaskTaskId(Long taskId);
    @Query("select j from AiJob j join fetch j.task t where j.dispatchedAt is null and j.resultHash is null and t.status = com.example.cast.research.entity.ResearchTaskStatus.RUNNING order by j.createdAt")
    List<AiJob> pending(org.springframework.data.domain.Pageable pageable);
}
