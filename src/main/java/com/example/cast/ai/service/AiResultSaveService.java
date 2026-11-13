
package com.example.cast.ai.service;

import com.example.cast.ai.dto.AiGenerateResponse;

import com.example.cast.claim.dto.request.ClaimInputs;
import com.example.cast.claim.dto.request.ClaimRequest;
import com.example.cast.claim.dto.request.VerificationResultRequest;

import com.example.cast.claim.entity.Claim;
import com.example.cast.claim.entity.ClaimType;
import com.example.cast.claim.entity.EvidenceRelation;
import com.example.cast.claim.entity.Verification;
import com.example.cast.claim.entity.VerificationMethod;
import com.example.cast.claim.entity.VerificationStatus;

import com.example.cast.claim.service.ClaimService;
import com.example.cast.claim.service.ClaimWriteService;
import com.example.cast.claim.service.VerificationService;

import com.example.cast.research.dto.request.ResearchInputs;
import com.example.cast.research.entity.Answer;
import com.example.cast.research.repository.AnswerRepository;
import com.example.cast.research.service.ResearchWriteService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiResultSaveService {

    private final ResearchWriteService researchWriteService;
    private final AnswerRepository answerRepository;
    private final ClaimService claimService;
    private final VerificationService verificationService;
    private final ClaimWriteService claimWriteService;

    @Transactional
    public Long save(
            Long taskId,
            Long runId,
            AiGenerateResponse response
    ) {

        // 1. 중복 Answer 확인
        if (answerRepository.existsByTaskTaskId(taskId)) {
            throw new IllegalStateException(
                    "이미 AI 답변이 저장된 Task입니다. taskId=" + taskId
            );
        }

        // 2. Answer 생성
        Answer answer =
                researchWriteService.createAnswer(taskId);

        Long answerId = answer.getAnswerId();

        // 3. Claim 및 검증 결과 저장
        if (response.claims() != null) {

            int index = 0;

            for (AiGenerateResponse.ClaimResult claimResult
                    : response.claims()) {

                ClaimType claimType = ClaimType.valueOf(
                        claimResult.claimType()
                );

                // 임시 정규화 값: 원문 보존
                Map<String, Object> claimValue = Map.of(
                        "raw_text",
                        claimResult.content()
                );

                ClaimRequest claimRequest = new ClaimRequest(
                        claimResult.content(),
                        claimType,
                        claimValue
                );

                Claim claim = claimService.create(
                        answerId,
                        index,
                        claimRequest
                );

                index++;

                // 4. Verification 시작
                Verification verification =
                        verificationService.start(
                                claim.getClaimId(),
                                runId
                        );

                VerificationStatus status =
                        VerificationStatus.valueOf(
                                claimResult.verificationStatus()
                        );

                // 5. Verification 완료
                VerificationResultRequest verificationRequest =
                        new VerificationResultRequest(
                                status,
                                null,
                                VerificationMethod.LLM,
                                null,
                                status,
                                null
                        );

                verificationService.complete(
                        verification.getVerificationId(),
                        verificationRequest
                );

                // 6. Evidence 저장
                if (claimResult.evidence() != null) {

                    for (AiGenerateResponse.EvidenceResult evidence
                            : claimResult.evidence()) {

                        EvidenceRelation relation =
                                EvidenceRelation.valueOf(
                                        evidence.relation()
                                );

                        ClaimInputs.EvidenceInput evidenceInput =
                                new ClaimInputs.EvidenceInput(
                                        evidence.excerpt(),
                                        relation
                                );

                        claimWriteService.addEvidence(
                                verification.getVerificationId(),
                                evidence.chunkId(),
                                evidenceInput
                        );
                    }
                }
            }
        }

        // 7. 최종 답변 확정
        ResearchInputs.FinalAnswer finalAnswer =
                new ResearchInputs.FinalAnswer(
                        response.answer(),
                        response.summary()
                );

        researchWriteService.finalizeAnswer(
                answerId,
                finalAnswer
        );

        return answerId;
    }
}
