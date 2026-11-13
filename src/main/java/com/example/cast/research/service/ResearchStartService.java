package com.example.cast.research.service;

import com.example.cast.integration.ai.service.AiJobService;
import com.example.cast.research.dto.request.ResearchInputs;
import com.example.cast.research.dto.response.ResearchStartResponse;
import com.example.cast.research.entity.ResearchQuery;
import com.example.cast.research.entity.ResearchTask;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResearchStartService {

    private final ResearchWriteService researchWriteService;
    private final AiJobService aiJobService;

    @Transactional
    public ResearchStartResponse start(
            Long userId,
            ResearchInputs.Query input
    ) {
        ResearchQuery query =
                researchWriteService.createQuery(userId, input);

        ResearchTask task =
                researchWriteService.createTask(query.getQueryId());

        aiJobService.enqueue(task.getTaskId());

        return new ResearchStartResponse(
                query.getQueryId(),
                task.getTaskId()
        );
    }
}
