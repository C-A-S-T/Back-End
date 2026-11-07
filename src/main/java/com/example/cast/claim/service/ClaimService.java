package com.example.cast.claim.service;
import com.example.cast.claim.entity.Claim;
import com.example.cast.claim.dto.request.ClaimRequest;
import com.example.cast.claim.repository.*;
import com.example.cast.claim.code.ClaimErrorCode;
import com.example.cast.research.repository.AnswerRepository;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** Claim 내용 변경과 최초 검증 시작이 같은 행 잠금을 사용한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimService {
    private final ClaimRepository claims;
    private final VerificationRepository verifications;
    private final AnswerRepository answers;
    private final RequestValidator validator;
    @Transactional
    public Claim create(Long answerId, int index, ClaimRequest request) {
        validator.requireId(answerId);
        validator.validate(request);
        if (index < 0) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        var answer = answers.findById(answerId).orElseThrow(() -> new BusinessException(GlobalErrorCode.NOT_FOUND));
        Claim claim = new Claim(answer, index, request.content());
        claim.revise(request.content(), request.type(), request.value());
        return claims.save(claim);
    }
    @Transactional
    public void revise(Long claimId, ClaimRequest request) {
        validator.requireId(claimId);
        validator.validate(request);
        Claim claim = claims.findLockedById(claimId).orElseThrow(() -> new BusinessException(ClaimErrorCode.CLAIM_NOT_FOUND));
        if (verifications.existsByClaimClaimId(claimId))
            throw new BusinessException(ClaimErrorCode.CLAIM_ALREADY_VERIFIED);
        claim.revise(request.content(), request.type(), request.value());
    }
}
