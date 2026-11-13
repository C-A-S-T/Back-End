package com.example.cast.web;

import com.example.cast.persistence.TestDatabaseSupport;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.document.code.DocumentSuccessCode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.*;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 공통 API 응답 계약 검증.
 *
 * 성공/실패 응답, JWT 인증, 예외 처리 및
 * AI 콜백 응답이 동일한 JSON 규격을 사용하는지 확인한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(ApiContractIntegrationTest.ProbeController.class)
class ApiContractIntegrationTest extends TestDatabaseSupport {

    @Autowired MockMvc mvc;

    @Autowired
    com.example.cast.auth.service.AuthService auth;

    private String bearer() {
        return "Bearer " + auth.signup(
                new com.example.cast.auth.dto.request.SignupRequest(
                        java.util.UUID.randomUUID() + "@example.invalid",
                        "ContractPassword123!"
                )
        ).accessToken();
    }

    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class ProbeController {

        record Input(@NotBlank String title) {}

        @PostMapping("/test-contract/input")
        ResponseEntity<ApiResponse<Input>> input(
                @Valid @RequestBody Input input
        ) {
            return ApiResponse.success(
                    DocumentSuccessCode.DOCUMENT_CREATED,
                    input
            );
        }

        @GetMapping("/test-contract/business")
        void business() {
            throw new BusinessException(
                    GlobalErrorCode.DATA_CONFLICT
            );
        }

        @GetMapping("/test-contract/failure")
        void failure() {
            throw new IllegalStateException(
                    "internal-secret-must-not-leak"
            );
        }
    }

    @Test
    void anonymousApiGetsCommon401() throws Exception {
        mvc.perform(
                        get("/test-contract/business")
                                .accept(MediaType.APPLICATION_JSON)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON401"))
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.timestamp").isString())
                .andExpect(jsonPath("$.result").isMap());
    }

    @Test
    void browserAlsoGetsJson401() throws Exception {
        mvc.perform(
                        get("/test-contract/business")
                                .accept(MediaType.TEXT_HTML)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"))
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void badBasicCredentialsGetCommon401() throws Exception {
        mvc.perform(
                        get("/test-contract/business")
                                .with(httpBasic("invalid", "invalid"))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    @Test
    void bearerPostDoesNotRequireCsrf() throws Exception {
        mvc.perform(
                        post("/test-contract/input")
                                .header("Authorization", bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"value\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("COMMON201"));
    }

    @Test
    void validInputGetsCommonSuccess() throws Exception {
        mvc.perform(
                        post("/test-contract/input")
                                .header("Authorization", bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\"value\"}")
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON201"))
                .andExpect(jsonPath("$.result.title").value("value"))
                .andExpect(jsonPath("$.timestamp").isString());
    }

    @Test
    void invalidInputGetsCommon400() throws Exception {
        mvc.perform(
                        post("/test-contract/input")
                                .header("Authorization", bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"title\":\" \"}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON400"))
                .andExpect(jsonPath("$.result").isMap());
    }

    @Test
    void malformedJsonGetsCommon400() throws Exception {
        mvc.perform(
                        post("/test-contract/input")
                                .header("Authorization", bearer())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    void missingRouteGetsCommon404() throws Exception {
        mvc.perform(
                        get("/test-contract/missing")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON404"));
    }

    @Test
    void wrongMethodGetsCommon405() throws Exception {
        mvc.perform(
                        get("/test-contract/input")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("COMMON405"));
    }

    @Test
    void businessFailureGetsCommon409() throws Exception {
        mvc.perform(
                        get("/test-contract/business")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.isSuccess").value(false))
                .andExpect(jsonPath("$.code").value("COMMON409"))
                .andExpect(jsonPath("$.result").isMap());
    }

    @Test
    void internalFailureIsRedacted() throws Exception {
        mvc.perform(
                        get("/test-contract/failure")
                                .header("Authorization", bearer())
                )
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON500"))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(
                                org.hamcrest.Matchers.containsString(
                                        "internal-secret"
                                )
                        )
                ));
    }

    @Test
    void healthRemainsPublic() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    private static final String CALLBACK_TOKEN =
            TEST_CALLBACK_TOKEN;

    @Autowired
    com.example.cast.integration.ai.service.AiJobService aiJobs;

    @Autowired
    jakarta.persistence.EntityManager em;

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactions;

    private com.example.cast.integration.ai.dto.AiJobRequest prepareJob() {

        Long taskId =
                new org.springframework.transaction.support.TransactionTemplate(
                        transactions
                ).execute(tx -> {

                    var user = new com.example.cast.user.entity.User(
                            java.util.UUID.randomUUID() + "@example.invalid",
                            "hash"
                    );

                    em.persist(user);

                    var query =
                            new com.example.cast.research.entity.ResearchQuery(
                                    user,
                                    "question"
                            );

                    em.persist(query);

                    var task =
                            new com.example.cast.research.entity.ResearchTask(
                                    query
                            );

                    em.persist(task);

                    return task.getTaskId();
                });

        return aiJobs.enqueue(taskId);
    }

    @Test
    void userLoginCannotImpersonateAiService() throws Exception {
        mvc.perform(
                        post("/internal/ai/research-tasks/1/result")
                                .with(user("tester").roles("AI_SERVER"))
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    @Test
    void wrongAiTokenRejected() throws Exception {
        mvc.perform(
                        post("/internal/ai/research-tasks/1/result")
                                .header("X-AI-Service-Token", "wrong")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    @Test
    void authenticatedAiPayloadIsValidated() throws Exception {
        mvc.perform(
                        post("/internal/ai/research-tasks/1/result")
                                .header(
                                        "X-AI-Service-Token",
                                        CALLBACK_TOKEN
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));
    }

    @Test
    void authenticatedCallbackSucceedsWithoutCsrfAndCanBeRetried()
            throws Exception {

        var job = prepareJob();

        String json = """
                {
                  "jobId": "%s",
                  "outcome": "SUCCESS",
                  "answer": "answer",
                  "summary": "summary",
                  "claims": []
                }
                """.formatted(job.jobId());

        for (int attempt = 0; attempt < 2; attempt++) {

            mvc.perform(
                            post(
                                    "/internal/ai/research-tasks/"
                                            + job.taskId()
                                            + "/result"
                            )
                                    .header(
                                            "X-AI-Service-Token",
                                            CALLBACK_TOKEN
                                    )
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json)
                    )
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.isSuccess").value(true))
                    .andExpect(jsonPath("$.code").value("COMMON200"))
                    .andExpect(
                            jsonPath("$.result.status")
                                    .value("COMPLETED")
                    )
                    .andExpect(
                            jsonPath("$.result.answerId")
                                    .isNumber()
                    );
        }
    }

    @Test
    void callbackWithWrongCorrelationIdReturnsConflict()
            throws Exception {

        var job = prepareJob();

        String json = """
                {
                  "jobId": "%s",
                  "outcome": "SUCCESS",
                  "answer": "answer",
                  "claims": []
                }
                """.formatted(java.util.UUID.randomUUID());

        mvc.perform(
                        post(
                                "/internal/ai/research-tasks/"
                                        + job.taskId()
                                        + "/result"
                        )
                                .header(
                                        "X-AI-Service-Token",
                                        CALLBACK_TOKEN
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("COMMON409"));
    }
}