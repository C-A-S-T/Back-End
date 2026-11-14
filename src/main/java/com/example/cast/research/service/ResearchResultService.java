package com.example.cast.research.service;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.research.dto.response.ResearchResultResponse;
import com.example.cast.research.entity.Answer;
import com.example.cast.research.entity.AnswerStatus;
import com.example.cast.research.entity.ResearchTask;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResearchResultService {

    private final EntityManager em;

    public ResearchResultResponse getResult(Long userId, Long taskId) {
        if (userId == null || userId <= 0 || taskId == null || taskId <= 0) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }

        ResearchTask task = em.find(ResearchTask.class, taskId);

        if (task == null) {
            throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        }

        if (!task.getQuery().getUser().getUserId().equals(userId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        Answer answer = em.createQuery(
                "SELECT a FROM Answer a WHERE a.task.taskId = :taskId",
                Answer.class
        ).setParameter("taskId", taskId)
         .getResultStream()
         .findFirst()
         .orElse(null);

        boolean isFinal = answer != null
                && answer.getAnswerStatus() == AnswerStatus.FINAL;

        return new ResearchResultResponse(
                task.getTaskId(),
                task.getQuery().getQueryId(),
                task.getStatus(),
                task.getCurrentStep(),
                task.getPreviewSummary(),
                isFinal ? answer.getAnswerContent() : null,
                isFinal ? answer.getSummaryContent() : null
        );
    }
}
