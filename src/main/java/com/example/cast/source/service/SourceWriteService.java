package com.example.cast.source.service;
import com.example.cast.source.entity.*;
import com.example.cast.source.dto.request.SourceInputs;
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
public class SourceWriteService {
    private final EntityManager em;
    private final RequestValidator validator;
    @Transactional
    public SourceFolder createFolder(Long userId, SourceInputs.Folder input) {
        validator.validate(input);
        SourceFolder folder = new SourceFolder(find(com.example.cast.user.entity.User.class, userId), input.name());
        em.persist(folder); return folder;
    }
    @Transactional
    public void renameFolder(Long id, SourceInputs.Folder input) {
        validator.validate(input); find(SourceFolder.class, id).rename(input.name());
    }
    @Transactional
    public SavedSource saveManually(Long userId, Long documentId) {
        SavedSource source = SavedSource.manualSave(find(com.example.cast.user.entity.User.class, userId),
                find(com.example.cast.document.entity.Document.class, documentId));
        em.persist(source); return source;
    }
    @Transactional
    public SavedSource saveAutomatically(Long userId, Long documentId, Long taskId) {
        var task = find(com.example.cast.research.entity.ResearchTask.class, taskId);
        if (!task.getQuery().getUser().getUserId().equals(userId)) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        SavedSource source = SavedSource.autoSave(find(com.example.cast.user.entity.User.class, userId),
                find(com.example.cast.document.entity.Document.class, documentId), task);
        em.persist(source); return source;
    }
    @Transactional
    public void move(Long id, SourceInputs.Move input) {
        validator.validate(input);
        SavedSource source = find(SavedSource.class, id);
        if (input.folderId() != null && !find(SourceFolder.class, input.folderId()).getUser().getUserId().equals(source.getUser().getUserId()))
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        source.moveToFolder(input.folderId());
    }
    private <T> T find(Class<T> type, Long id) {
        if (id == null || id <= 0) throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        T entity = em.find(type, id);
        if (entity == null) throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        return entity;
    }
}
