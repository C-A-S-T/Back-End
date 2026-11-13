package com.example.cast.integration.ai.service;
import com.example.cast.integration.ai.client.AiServerClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
/** 전송 성공 후 상태 기록. 그 사이 장애가 나면 동일 jobId로 재전송한다. */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "cast.ai.enabled", havingValue = "true")
public class AiJobDispatcher {
    private final AiJobService jobs;
    private final AiServerClient client;
    @Scheduled(fixedDelayString = "${cast.ai.dispatch-delay-ms:5000}")
    public void dispatch() {
        for (var request : jobs.pending()) {
            try {
                client.submit(request);
                jobs.markDispatched(request);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); return;
            } catch (Exception e) {
                // DB에 남겨 다음 주기에 재시도한다. 원문/인증 토큰은 로그에 남기지 않는다.
                log.warn("AI job delivery deferred: jobId={}, errorType={}", request.jobId(), e.getClass().getSimpleName());
            }
        }
    }
}
