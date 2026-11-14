package com.example.cast.user.service;
import com.example.cast.user.entity.*;
import com.example.cast.user.dto.request.UserInputs;
import com.example.cast.source.service.SourceFolderInitializer;
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
public class UserWriteService {
    private final EntityManager em;
    private final RequestValidator validator;
    private final SourceFolderInitializer folderInitializer;
    @Transactional
    public User create(UserInputs.Create input) {
        validator.validate(input);
        User user = new User(input.email(), input.passwordHash());
        em.persist(user);
        // Both the account and its three folders belong to the same transaction.
        folderInitializer.initialize(user);
        return user;
    }
    @Transactional
    public void changePassword(Long id, UserInputs.Password input) {
        validator.validate(input); find(User.class, id).updatePasswordHash(input.hash());
    }
    @Transactional
    public void changeStatus(Long id, UserInputs.Status input) {
        validator.validate(input); find(User.class, id).changeAccountStatus(input.status());
    }
    @Transactional
    public UserConsent recordConsent(Long userId, UserInputs.Consent input) {
        validator.validate(input);
        UserConsent consent = new UserConsent(find(User.class, userId), input.type(), input.version(), input.agreed(), input.occurredAt());
        em.persist(consent); return consent;
    }
    private <T> T find(Class<T> type, Long id) {
        if (id == null || id <= 0) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        T entity = em.find(type, id);
        if (entity == null) throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        return entity;
    }
}
