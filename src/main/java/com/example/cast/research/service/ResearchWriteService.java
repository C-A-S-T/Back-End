package com.example.cast.research.service;
import com.example.cast.research.entity.*;
import com.example.cast.research.dto.request.ResearchInputs;
import com.example.cast.global.validation.RequestValidator;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.code.GlobalErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/** 내부 쓰기 진입점. HTTP 계층에서는 인증 소유권 확인 후 호출하고 DTO로 변환한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResearchWriteService {
    private final EntityManager em;
    private final RequestValidator validator;
    @Transactional
    public ResearchQuery createQuery(Long userId, ResearchInputs.Query input) {
        validator.validate(input);
        ResearchQuery query = new ResearchQuery(find(com.example.cast.user.entity.User.class, userId), input.question());
        em.persist(query); return query;
    }
    @Transactional
    public ResearchTask createTask(Long queryId) {
        ResearchTask task = new ResearchTask(find(ResearchQuery.class, queryId)); em.persist(task); return task;
    }
    @Transactional
    public Answer createAnswer(Long taskId) {
        Answer answer = new Answer(find(ResearchTask.class, taskId)); em.persist(answer); return answer;
    }
    @Transactional
    public void updateProgress(Long taskId, ResearchInputs.Progress input) {
        validator.validate(input); find(ResearchTask.class, taskId).updateProgress(input.step(), input.summary());
    }
    @Transactional
    public void finalizeAnswer(Long answerId, ResearchInputs.FinalAnswer input) {
        validator.validate(input); find(Answer.class, answerId).markFinal(input.content(), input.summary());
    }
    private <T> T find(Class<T> type, Long id) {
        if (id == null || id <= 0) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        T entity = em.find(type, id);
        if (entity == null) throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        return entity;
    }
}
