package com.example.cast.integration.ai.service;
import com.example.cast.integration.ai.dto.*;
import com.example.cast.integration.ai.repository.AiJobRepository;
import com.example.cast.claim.entity.*;
import com.example.cast.document.dto.SourceSnapshot;
import com.example.cast.document.entity.Document;
import com.example.cast.document.service.DocumentSnapshotService;
import com.example.cast.research.entity.*;
import com.example.cast.research.repository.*;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import java.security.*;
import java.util.*;
/** 작업/결과를 한 트랜잭션으로 저장한다. AI 스키마에는 접근하지 않는다. */
@Service
@RequiredArgsConstructor
public class AiResultService {
    private final ResearchTaskRepository tasks;
    private final AnswerRepository answers;
    private final AiJobRepository jobs;
    private final DocumentSnapshotService documents;
    private final RequestValidator validator;
    private final EntityManager em;
    private final ObjectMapper mapper;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiResultReceipt accept(Long taskId, AiResultRequest request) {
        validator.requireId(taskId);
        validator.validate(request);
        var task = tasks.findLockedById(taskId).orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        var job = jobs.findByTaskTaskId(taskId).orElseThrow(() -> new BusinessException(GlobalErrorCode.DATA_CONFLICT));
        if (!job.getJobId().equals(request.jobId())) throw new BusinessException(GlobalErrorCode.DATA_CONFLICT);
        String hash = fingerprint(request);
        if (job.getResultHash() != null) {
            if (!job.getResultHash().equals(hash)) throw new BusinessException(GlobalErrorCode.DATA_CONFLICT);
            return receipt(task);
        }
        if (task.getStatus() != ResearchTaskStatus.RUNNING || answers.findByTaskTaskId(taskId).isPresent())
            throw new BusinessException(GlobalErrorCode.DATA_CONFLICT);
        if (request.outcome() == AiResultRequest.Outcome.FAILURE) {
            // AI 내부 로그/경로를 사용자 작업 메시지로 복사하지 않는다.
            task.markFailed("AI 조사 작업을 완료하지 못했습니다.");
        } else {
            // 출처 잠금 획득 순서를 맞춰 서로 다른 작업의 동시 수신도 교착을 피한다.
            var sources = new TreeMap<Long, SourceSnapshot>();
            for (var claim : request.claims()) for (var citation : claim.citations()) {
                var source = citation.source();
                var previous = sources.putIfAbsent(source.aiDocumentId(), source);
                if (previous != null && !previous.equals(source)) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
            }
            var snapshots = new HashMap<Long, Document>();
            sources.forEach((id, source) -> snapshots.put(id, documents.capture(source)));
            var answer = new Answer(task);
            answer.markFinal(request.answer(), request.summary());
            em.persist(answer);
            int index = 0;
            for (var result : request.claims()) {
                var claim = new Claim(answer, index++, result.content());
                claim.revise(result.content(), result.type(), result.value());
                claim.confirm();
                em.persist(claim);
                var verification = new Verification(claim, 1);
                verification.linkAiRun(result.aiRunId());
                var judgment = result.verification();
                verification.complete(judgment.status(), judgment.reason(), judgment.method(),
                        judgment.ruleResult(), judgment.llmResult(), judgment.confidence());
                em.persist(verification);
                for (var citation : result.citations()) {
                    em.persist(new Evidence(verification, snapshots.get(citation.source().aiDocumentId()),
                            citation.aiChunkId(), citation.text(), citation.relation(),
                            citation.pageStart(), citation.pageEnd(), citation.sectionTitle()));
                }
            }
            task.markCompleted();
        }
        job.accept(hash, request.outcome());
        return receipt(task);
    }
    private AiResultReceipt receipt(ResearchTask task) {
        return new AiResultReceipt(task.getTaskId(), answers.findByTaskTaskId(task.getTaskId())
                .map(Answer::getAnswerId).orElse(null), task.getStatus());
    }
    private String fingerprint(AiResultRequest request) {
        try {
            byte[] json = mapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS).writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
