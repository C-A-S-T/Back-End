package com.example.cast.integration.ai.service;

import com.example.cast.integration.ai.config.AiDispatchProtectionProperties;
import com.example.cast.integration.ai.dto.AiProgressRequest;
import com.example.cast.integration.ai.dto.AiProgressReceipt;
import com.example.cast.integration.ai.repository.AiJobRepository;
import com.example.cast.research.entity.ResearchTask;
import com.example.cast.research.entity.ResearchTaskStatus;
import com.example.cast.research.repository.ResearchTaskRepository;
import com.example.cast.research.repository.AnswerRepository;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.validation.RequestValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiProgressService {

    private final ResearchTaskRepository tasks;
    private final AnswerRepository answers;
    private final AiJobRepository jobs;
    private final RequestValidator validator;
    private final AiDispatchProtectionProperties dispatchProtection;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AiProgressReceipt accept(
            Long taskId,
            AiProgressRequest request
    ) {
        validator.requireId(taskId);
        validator.validate(request);

        var task = tasks.findLockedById(taskId)
                .orElseThrow(() ->
                        new BusinessException(GlobalErrorCode.NOT_FOUND));

        var job = jobs.findByTaskTaskId(taskId)
                .orElseThrow(() ->
                        new BusinessException(GlobalErrorCode.DATA_CONFLICT));

        if (!job.getJobId().equals(request.jobId())) {
            throw conflict();
        }

        var gate = dispatchProtection.requiredGate();

        if (gate.protectedJobIds().contains(job.getJobId())
                || job.getCreatedAt() == null
                || !job.getCreatedAt().isAfter(gate.cutoverAt())) {
            throw conflict();
        }

        if (job.getResultHash() != null
                || task.getStatus() != ResearchTaskStatus.RUNNING
                || answers.findByTaskTaskId(taskId).isPresent()) {
            throw conflict();
        }

        Short previousStep = task.getCurrentStep();
        Short nextStep = request.currentStep();

        if (previousStep != null && nextStep < previousStep) {
            throw conflict();
        }

        String previousSummary = task.getPreviewSummary();
        String nextSummary = request.previewSummary();

        if (nextSummary == null || nextSummary.isBlank()) {
            nextSummary = previousSummary;
        } else {
            nextSummary = nextSummary.strip();
        }

        if (Objects.equals(previousStep, nextStep)
                && Objects.equals(previousSummary, nextSummary)) {
            return receipt(task);
        }

        task.updateProgress(nextStep, nextSummary);

        return receipt(task);
    }

    private BusinessException conflict() {
        return new BusinessException(GlobalErrorCode.DATA_CONFLICT);
    }

    private AiProgressReceipt receipt(ResearchTask task) {
        return new AiProgressReceipt(
                task.getTaskId(),
                task.getStatus(),
                task.getCurrentStep(),
                task.getPreviewSummary()
        );
    }
}
