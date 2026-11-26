package com.example.cast.integration.ai.service;

import com.example.cast.global.validation.RequestValidator;
import com.example.cast.integration.ai.config.AiDispatchProtectionProperties;
import com.example.cast.integration.ai.config.AiProperties;
import com.example.cast.integration.ai.dto.AiJobRequest;
import com.example.cast.integration.ai.entity.AiJob;
import com.example.cast.integration.ai.repository.AiJobRepository;
import com.example.cast.research.repository.AnswerRepository;
import com.example.cast.research.repository.ResearchTaskRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AiJobServiceDispatchProtectionTest {
    private static final String LEGACY_ID = "00000000-0000-0000-0000-000000000001";
    private static final Instant CUTOVER = Instant.parse("2026-12-01T00:00:00Z");

    private AiJobService service(AiJobRepository repo, boolean enabled,
            AiDispatchProtectionProperties protection) {
        return new AiJobService(repo, mock(ResearchTaskRepository.class), mock(AnswerRepository.class),
                mock(RequestValidator.class), mock(EntityManager.class), Clock.systemUTC(),
                new AiProperties(enabled, null, "", ""), protection);
    }

    @Test
    void onlyDatabaseFilteredJobsArePassedToDispatcher() {
        var repo = mock(AiJobRepository.class);
        var protection = new AiDispatchProtectionProperties(CUTOVER.toString(), LEGACY_ID);
        var request = new AiJobRequest(UUID.randomUUID(), 42L, "test question");
        var job = mock(AiJob.class);
        when(job.request()).thenReturn(request);
        when(repo.pendingEligible(eq(CUTOVER), eq(List.of(UUID.fromString(LEGACY_ID))),
                any(Pageable.class))).thenReturn(List.of(job));

        var service = service(repo, false, protection);
        assertEquals(List.of(request), service.pending());
        verify(repo).pendingEligible(eq(CUTOVER), eq(List.of(UUID.fromString(LEGACY_ID))),
                argThat(page -> page.getPageSize() == 20));
    }

    @Test
    void enablingDispatcherWithoutProtectionFailsAtInitialization() {
        var repo = mock(AiJobRepository.class);
        var service = service(repo, true, new AiDispatchProtectionProperties(null, null));
        assertThrows(IllegalStateException.class, service::verifyProtectionForEnabledDispatcher);
        assertThrows(IllegalStateException.class, service::pending);
        verifyNoInteractions(repo);
    }

    @Test
    void disabledDispatcherMayStartWithoutProtection() {
        var service = service(mock(AiJobRepository.class), false,
                new AiDispatchProtectionProperties(null, null));
        assertDoesNotThrow(service::verifyProtectionForEnabledDispatcher);
    }
}
