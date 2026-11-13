package com.example.cast.integration;
import com.example.cast.integration.ai.client.AiServerClient;
import com.example.cast.integration.ai.config.AiProperties;
import com.example.cast.integration.ai.dto.AiJobRequest;
import com.example.cast.integration.ai.service.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
/** 가짜 HTTP 수신 서버로 계약/재전송/트랜잭션 경계를 검증한다. */
class AiDeliveryTest {
    @Test void httpClientSendsStableJobIdAndServiceToken() throws Exception {
        var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var requests=new AtomicInteger();var received=new java.util.concurrent.atomic.AtomicReference<String>();
        var token=new java.util.concurrent.atomic.AtomicReference<String>();
        server.createContext("/v1/research-jobs", exchange -> {
            received.set(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            token.set(exchange.getRequestHeaders().getFirst("X-AI-Service-Token"));
            requests.incrementAndGet();exchange.sendResponseHeaders(202,-1);exchange.close();
        });server.start();
        try {
            var properties=new AiProperties(true,URI.create("http://127.0.0.1:"+server.getAddress().getPort()),"outgoing-test-token-at-least-32-characters","callback-test-token-at-least-32-characters");
            var client=new AiServerClient(properties,JsonMapper.builder().build());
            var job=new AiJobRequest(UUID.randomUUID(),1L,"question");client.submit(job);client.submit(job);
            assertEquals(2,requests.get());assertTrue(received.get().contains(job.jobId().toString()));assertEquals(properties.apiToken(),token.get());
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {assertThrows(IllegalStateException.class,()->client.submit(job));}
            finally {TransactionSynchronizationManager.setActualTransactionActive(false);}
        } finally {server.stop(0);}
    }
    @Test void failedDeliveryRemainsPendingAndRetryUsesSameJob() throws Exception {
        var jobs=mock(AiJobService.class);var client=mock(AiServerClient.class);
        var request=new AiJobRequest(UUID.randomUUID(),1L,"question");when(jobs.pending()).thenReturn(List.of(request));
        doThrow(new IOException("unavailable")).doNothing().when(client).submit(request);
        var dispatcher=new AiJobDispatcher(jobs,client);dispatcher.dispatch();verify(jobs,never()).markDispatched(request);
        dispatcher.dispatch();verify(client,times(2)).submit(request);verify(jobs).markDispatched(request);
    }
}
