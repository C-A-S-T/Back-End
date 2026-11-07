package com.example.cast.claim.service;
import com.example.cast.claim.entity.*;
import com.example.cast.claim.dto.request.VerificationResultRequest;
import com.example.cast.claim.repository.*;
import com.example.cast.claim.code.ClaimErrorCode;
import com.example.cast.retrieval.repository.RetrievalRunRepository;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 검증 시도 번호는 검색 재시도 번호와 독립적으로 할당한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VerificationService {
    private final ClaimRepository claims;
    private final VerificationRepository verifications;
    private final RetrievalRunRepository runs;
    private final RequestValidator validator;
    @Transactional
    public Verification start(Long claimId, Long runId) {
        validator.requireId(claimId);
        Claim claim = claims.findLockedById(claimId).orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_NOT_FOUND));
        Verification verification = new Verification(claim, verifications.latestAttempt(claimId) + 1);
        if (runId != null) {
            validator.requireId(runId);
            var run = runs.findById(runId).orElseThrow(() -> new BusinessException(ClaimErrorCode.VERIFICATION_RUN_MISMATCH));
            if (!run.getTask().getTaskId().equals(claim.getAnswer().getTask().getTaskId())
                    || (run.getClaim() != null && !run.getClaim().getClaimId().equals(claimId)))
                throw new BusinessException(ClaimErrorCode.VERIFICATION_RUN_MISMATCH);
            verification.linkRun(run);
        }
        return verifications.save(verification);
    }
    @Transactional
    public void complete(Long id, VerificationResultRequest request) {
        validator.requireId(id);
        validator.validate(request);
        var verification = verifications.findLockedById(id)
                .orElseThrow(() -> new BusinessException(ClaimErrorCode.VERIFICATION_NOT_FOUND));
        if (verification.isCompleted()) throw new BusinessException(
                com.example.cast.global.code.GlobalErrorCode.DATA_CONFLICT);
        verification.complete(request.status(), request.reason(), request.method(),
                request.ruleResult(), request.llmResult(), request.confidence());
    }
}
