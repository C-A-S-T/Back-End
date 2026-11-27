package com.example.cast.research.service;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.research.dto.response.ResearchListResponse;
import com.example.cast.research.dto.response.ResearchListResponse.ResearchItem;
import com.example.cast.research.repository.ResearchTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ResearchListService {

    private final ResearchTaskRepository tasks;

    public ResearchListResponse getList(Long userId, int page, int size) {
        if (userId == null || userId <= 0
                || page < 0 || size < 1 || size > 100) {
            throw new BusinessException(GlobalErrorCode.INVALID_REQUEST);
        }

        var pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt", "taskId"));

        var result = tasks.findByQuery_User_UserId(userId, pageable);

        var items = result.getContent().stream()
                .map(task -> new ResearchItem(
                        task.getTaskId(),
                        task.getQuery().getQuestion(),
                        task.getStatus(),
                        task.getCreatedAt()
                )).toList();

        return new ResearchListResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }
}
