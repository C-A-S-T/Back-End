package com.example.cast.integration.ai.service;
import com.example.cast.integration.ai.dto.AiJobRequest;
import com.example.cast.integration.ai.entity.AiJob;
import com.example.cast.integration.ai.repository.AiJobRepository;
import com.example.cast.research.repository.*;
import com.example.cast.research.entity.ResearchTaskStatus;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
/** 작업 생성과 전송 상태 기록은 짧은 DB 트랜잭션에서 수행한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiJobService {
    private final AiJobRepository jobs;
    private final ResearchTaskRepository tasks;
    private final AnswerRepository answers;
    private final RequestValidator validator;
    private final EntityManager em;
    private final Clock clock;

    /** HTTP 호출 없이 작업만 등록한다. 반복 호출은 동일 jobId를 반환한다. */
    @Transactional
    public AiJobRequest enqueue(Long taskId) {
        validator.requireId(taskId);
        var task = tasks.findLockedById(taskId).orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        var existing = jobs.findByTaskTaskId(taskId);
        if (existing.isPresent()) return existing.get().request();
        if (task.getStatus() != ResearchTaskStatus.PENDING || answers.findByTaskTaskId(taskId).isPresent())
            throw new BusinessException(GlobalErrorCode.DATA_CONFLICT);
        task.start();
        var job = new AiJob(task);
        em.persist(job);
        return job.request();
    }
    public List<AiJobRequest> pending() {
        return jobs.pending(PageRequest.of(0, 20)).stream().map(AiJob::request).toList();
    }
    @Transactional
    public void markDispatched(AiJobRequest request) {
        tasks.findLockedById(request.taskId()).orElseThrow();
        jobs.findById(request.jobId()).orElseThrow().markDispatched(Instant.now(clock));
    }
}
