package com.example.cast.retrieval.service;
import com.example.cast.retrieval.entity.RetrievalRun;
import com.example.cast.retrieval.repository.RetrievalRunRepository;
import com.example.cast.retrieval.dto.request.RetrievalRunRequest;
import com.example.cast.retrieval.code.RetrievalErrorCode;
import com.example.cast.research.repository.ResearchTaskRepository;
import com.example.cast.claim.repository.ClaimRepository;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 다른 조사 작업의 Claim을 검색 이력에 연결하지 못하도록 검증한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RetrievalRunService {
    private final RetrievalRunRepository runs;
    private final ResearchTaskRepository tasks;
    private final ClaimRepository claims;
    private final RequestValidator validator;
    @Transactional
    public RetrievalRun create(RetrievalRunRequest request) {
        validator.validate(request);
        var task = tasks.findById(request.taskId()).orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        var claim = request.claimId() == null ? null : claims.findById(request.claimId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        if (claim != null && !claim.getAnswer().getTask().getTaskId().equals(task.getTaskId()))
            throw new BusinessException(RetrievalErrorCode.CLAIM_TASK_MISMATCH);
        var run = switch (request.type()) {
            case INITIAL -> RetrievalRun.initial(task, request.query());
            case CLAIM_SEARCH -> RetrievalRun.claimSearch(task, claim, request.query());
            case RETRY -> RetrievalRun.retry(task, claim, request.attempt(), request.query());
        };
        return runs.save(run);
    }
}
