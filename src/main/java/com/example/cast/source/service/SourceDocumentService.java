package com.example.cast.source.service;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.source.dto.response.SourceDocumentListResponse;
import com.example.cast.source.dto.response.SourceDocumentListResponse.DocumentItem;
import com.example.cast.source.entity.SavedSource;
import com.example.cast.source.repository.SourceDocumentRepository;
import com.example.cast.source.repository.SourceFolderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SourceDocumentService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final Sort SAVED_ORDER = Sort.by(
            Sort.Order.desc("createdAt"), Sort.Order.desc("savedSourceId"));

    private final SourceDocumentRepository documentRepository;
    private final SourceFolderRepository folderRepository;

    public SourceDocumentListResponse listDocuments(Long userId, Long folderId, int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE || (folderId != null && folderId <= 0)) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }
        if (folderId != null &&
                !folderRepository.existsByFolderIdAndUser_UserIdAndFolderCategoryIsNotNull(folderId, userId)) {
            // Do not reveal whether a folder belongs to another user.
            throw new BusinessException(GlobalErrorCode.NOT_FOUND);
        }

        var pageable = PageRequest.of(page, size, SAVED_ORDER);
        Page<SavedSource> saved = folderId == null
                ? documentRepository.findByUser_UserId(userId, pageable)
                : documentRepository.findByUser_UserIdAndFolderId(userId, folderId, pageable);

        return new SourceDocumentListResponse(
                saved.getContent().stream().map(this::toItem).toList(),
                saved.getNumber(), saved.getSize(), saved.getTotalElements(),
                saved.getTotalPages(), saved.hasNext()
        );
    }

    private DocumentItem toItem(SavedSource saved) {
        var document = saved.getDocument();
        return new DocumentItem(
                saved.getSavedSourceId(),
                document.getDocumentId(),
                document.getDocTitle(),
                document.getSourceName(),
                document.getSourceUrl(),
                document.getPublishedAt(),
                document.getSourceType(),
                document.getReliabilityLevel(),
                saved.getSaveMethod(),
                saved.getFolderId(),
                saved.isFavorite(),
                saved.getCreatedAt()
        );
    }
}
