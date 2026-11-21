package com.example.cast.integration.ai.client;

import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.integration.ai.config.AiProperties;
import com.example.cast.policycompare.code.PolicyComparisonErrorCode;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 루프백 모의 HTTP 서버만 사용한다. 외부 FastAPI 및 DB 연결은 없다. */
class PolicyComparisonClientTest {
    private static final String POLICY_KEY = "seoul-youth-rent";
    private static final String PATH = "/v1/policy-comparisons/" + POLICY_KEY;
    private static final String LEGACY_PATH = PATH + "/preview";
    private static final String TOKEN = "test-service-token-" + "x".repeat(32);
    private static final String RESPONSE = """
            {
              "policyKey":"seoul-youth-rent",
              "policyTitle":"Youth rent support",
              "previousDocumentKey":"DOC-2025",
              "currentDocumentKey":"DOC-2026",
              "reviewStatus":"REVIEW_REQUIRED",
              "humanApproved":false,
              "javaImportReady":false,
              "sourceProvenanceVerified":false,
              "warning":"Requires human review",
              "totalItems":0,
              "items":[]
            }
            """;

    private HttpServer server;
    private URI baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void sendsAuthenticatedGetAndParsesComparison() {
        var actualToken = new AtomicReference<String>();
        var actualMethod = new AtomicReference<String>();
        server.createContext(PATH, exchange -> {
            actualToken.set(exchange.getRequestHeaders().getFirst("X-AI-Service-Token"));
            actualMethod.set(exchange.getRequestMethod());
            respond(exchange, 200, RESPONSE);
        });
        server.start();

        var comparison = client(true, TOKEN).fetchComparison(POLICY_KEY);

        assertThat(actualToken.get()).isEqualTo(TOKEN);
        assertThat(actualMethod.get()).isEqualTo("GET");
        assertThat(comparison.policyKey()).isEqualTo(POLICY_KEY);
        assertThat(comparison.humanApproved()).isFalse();
        assertThat(comparison.javaImportReady()).isFalse();
        assertThat(comparison.items()).isEmpty();
    }

    @Test
    void fallsBackToLegacyFastApiRouteUntilAliasIsDeployed() {
        var fallbackCalls = new java.util.concurrent.atomic.AtomicInteger();
        server.createContext(PATH, exchange -> respond(exchange, 404, "{}"));
        server.createContext(LEGACY_PATH, exchange -> {
            fallbackCalls.incrementAndGet();
            respond(exchange, 200, RESPONSE);
        });
        server.start();

        var comparison = client(true, TOKEN).fetchComparison(POLICY_KEY);

        assertThat(comparison.policyKey()).isEqualTo(POLICY_KEY);
        assertThat(fallbackCalls.get()).isEqualTo(1);
    }

    @ParameterizedTest
    @CsvSource({
            "404, NOT_FOUND",
            "401, AI_BAD_GATEWAY",
            "500, AI_BAD_GATEWAY",
            "503, AI_UNAVAILABLE",
            "302, AI_BAD_GATEWAY"
    })
    void upstreamErrorsMapToSafeApplicationErrors(int upstreamStatus, String expected) {
        server.createContext(PATH, exchange -> respond(exchange, upstreamStatus,
                "upstream-secret-must-not-appear"));
        server.start();

        assertThatThrownBy(() -> client(true, TOKEN).fetchComparison(POLICY_KEY))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    var error = "NOT_FOUND".equals(expected)
                            ? GlobalErrorCode.NOT_FOUND
                            : PolicyComparisonErrorCode.valueOf(expected);
                    assertThat(e.getErrorCode()).isEqualTo(error);
                    assertThat(e.getMessage()).doesNotContain("upstream-secret");
                });
    }

    @Test
    void malformedJsonIs502WithoutBodyDisclosure() {
        server.createContext(PATH, exchange -> respond(exchange, 200,
                "{\"private\":\"upstream-secret\", bad-json}"));
        server.start();

        assertThatThrownBy(() -> client(true, TOKEN).fetchComparison(POLICY_KEY))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.getErrorCode())
                            .isEqualTo(PolicyComparisonErrorCode.AI_BAD_GATEWAY);
                    assertThat(e.getMessage()).doesNotContain("upstream-secret");
                });
    }

    @Test
    void oversizedBodyIs502() {
        server.createContext(PATH, exchange -> respond(exchange, 200, "x".repeat(8 * 1024 * 1024 + 1)));
        server.start();

        assertThatThrownBy(() -> client(true, TOKEN).fetchComparison(POLICY_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(PolicyComparisonErrorCode.AI_BAD_GATEWAY));
    }

    @Test
    void disabledAiIs503WithoutHttpRequest() {
        assertThatThrownBy(() -> client(false, TOKEN).fetchComparison(POLICY_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(PolicyComparisonErrorCode.AI_UNAVAILABLE));
    }

    @Test
    void missingServiceTokenIs503WithoutHttpRequest() {
        assertThatThrownBy(() -> client(true, "").fetchComparison(POLICY_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(PolicyComparisonErrorCode.AI_UNAVAILABLE));
    }

    @Test
    void refusedConnectionIs503() {
        // HTTP 서버를 닫으면 이미 사용한 루프백 포트로 연결할 수 없다.
        server.start();
        server.stop(0);
        server = null;
        assertThatThrownBy(() -> client(true, TOKEN).fetchComparison(POLICY_KEY))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode())
                                .isEqualTo(PolicyComparisonErrorCode.AI_UNAVAILABLE));
    }

    private PolicyComparisonClient client(boolean enabled, String token) {
        var properties = new AiProperties(enabled, baseUrl, token, "");
        return new PolicyComparisonClient(properties, new ObjectMapper());
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange,
            int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
