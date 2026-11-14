package com.example.cast.web;

import com.example.cast.auth.code.AuthErrorCode;
import com.example.cast.auth.code.AuthSuccessCode;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.persistence.TestDatabaseSupport;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Swagger 문서, 응답 예시, 공통 API 응답 규격 검증
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SwaggerIntegrationTest extends TestDatabaseSupport {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void swaggerUiAndConfigurationArePublic() throws Exception {

        mvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        org.hamcrest.Matchers.containsString("Swagger UI")
                ));

        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js"))
                .andExpect(status().isOk());

        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(
                        jsonPath("$.persistAuthorization").value(false)
                );
    }

    @Test
    void specDocumentsAuthSourcesAndResearchAndDoesNotRequireTokenForSignupOrLogin()
            throws Exception {

        JsonNode spec = spec();

        assertThat(spec.get("paths").size()).isEqualTo(9);
        JsonNode researchOperation = spec.at("/paths/~1api~1research-tasks~1{taskId}/get");
        assertThat(researchOperation.isMissingNode()).isFalse();
        assertThat(researchOperation.at("/security/0/bearerAuth").isArray()).isTrue();

        assertThat(spec.at(
                "/paths/~1api~1v1~1auth~1signup/post/security"
        ).isArray()).isTrue();

        assertThat(spec.at(
                "/paths/~1api~1v1~1auth~1signup/post/security"
        ).size()).isZero();

        assertThat(spec.at(
                "/paths/~1api~1v1~1auth~1login/post/security"
        ).isArray()).isTrue();

        assertThat(spec.at(
                "/paths/~1api~1v1~1auth~1login/post/security"
        ).size()).isZero();

        assertThat(spec.at(
                "/components/securitySchemes/bearerAuth/type"
        ).asText()).isEqualTo("http");

        assertThat(spec.at(
                "/components/securitySchemes/bearerAuth/scheme"
        ).asText()).isEqualTo("bearer");

        assertThat(spec.at(
                "/servers/0/url"
        ).asText()).isEqualTo("/");
    }

    @Test
    void overviewSwaggerHasBearerSecurityAndTypedResponse() throws Exception {
        JsonNode spec = spec();
        JsonNode operation = spec.at("/paths/~1api~1v1~1sources~1overview/get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/responses/200").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/401").isMissingNode()).isFalse();

        String schemaRef = operation.at(
                "/responses/200/content/application~1json/schema/$ref"
        ).asText();
        assertThat(schemaRef).startsWith("#/components/schemas/");
        assertThat(spec.at(schemaRef.substring(1))
                .at("/properties/result/$ref").asText())
                .isEqualTo("#/components/schemas/SourceOverviewResponse");
    }

    @Test
    void documentsSwaggerHasBearerAndTypedResponse() throws Exception {
        JsonNode spec = spec();
        JsonNode operation = spec.at("/paths/~1api~1v1~1sources~1documents/get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/parameters").isArray()).isTrue();
        assertThat(operation.at("/parameters").toString()).contains("folderId", "page", "size");
        for (String code : new String[] {"200", "400", "401", "404"}) {
            assertThat(operation.at("/responses/" + code).isMissingNode()).isFalse();
        }

        String responseSchemaRef = operation.at(
                "/responses/200/content/application~1json/schema/$ref"
        ).asString();
        assertThat(responseSchemaRef).startsWith("#/components/schemas/");
        assertThat(spec.at(responseSchemaRef.substring(1))
                .at("/properties/result/$ref").asString())
                .isEqualTo("#/components/schemas/SourceDocumentListResponse");
    }

    @Test
    void documentDetailSwaggerHasBearerAndTypedResponse() throws Exception {
        JsonNode spec = spec();
        JsonNode operation = spec.at("/paths/~1api~1v1~1sources~1documents~1{savedSourceId}/get");

        assertThat(operation.isMissingNode()).isFalse();
        assertThat(operation.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(operation.at("/parameters").toString()).contains("savedSourceId");
        for (String code : new String[] {"200", "400", "401", "404"}) {
            assertThat(operation.at("/responses/" + code).isMissingNode()).isFalse();
        }

        String schemaRef = operation.at(
                "/responses/200/content/application~1json/schema/$ref"
        ).asString();
        assertThat(schemaRef).startsWith("#/components/schemas/");
        assertThat(spec.at(schemaRef.substring(1))
                .at("/properties/result/$ref").asString())
                .isEqualTo("#/components/schemas/SourceDocumentDetailResponse");
    }

    @Test
    void scrapAndResearchEvidenceSwaggerHaveBearerAndTypedResponses() throws Exception {
        JsonNode spec = spec();
        JsonNode research = spec.at("/paths/~1api~1research-tasks~1{taskId}~1evidences/get");
        assertThat(research.isMissingNode()).isFalse();
        assertThat(research.at("/security/0/bearerAuth").isArray()).isTrue();
        String researchSchema = research.at("/responses/200/content/application~1json/schema/$ref").asString();
        assertThat(spec.at(researchSchema.substring(1)).at("/properties/result/$ref").asString())
                .isEqualTo("#/components/schemas/ResearchEvidenceResponse");

        JsonNode scraps = spec.at("/paths/~1api~1v1~1sources~1scraps");
        assertThat(scraps.at("/get").isMissingNode()).isFalse();
        assertThat(scraps.at("/post").isMissingNode()).isFalse();
        assertThat(scraps.at("/get/security/0/bearerAuth").isArray()).isTrue();
        assertThat(scraps.at("/post/security/0/bearerAuth").isArray()).isTrue();
        String listSchema = scraps.at("/get/responses/200/content/application~1json/schema/$ref").asString();
        assertThat(spec.at(listSchema.substring(1)).at("/properties/result/$ref").asString())
                .isEqualTo("#/components/schemas/SentenceScrapListResponse");
        JsonNode remove = spec.at("/paths/~1api~1v1~1sources~1scraps~1{scrapId}/delete");
        assertThat(remove.isMissingNode()).isFalse();
        assertThat(remove.at("/security/0/bearerAuth").isArray()).isTrue();
        assertThat(remove.at("/parameters").toString()).contains("scrapId");
    }

    @Test
    void responseExamplesAgreeWithActualCodesAndSuccessHasTypedData()
            throws Exception {

        JsonNode spec = spec();

        JsonNode signup = spec.at(
                "/paths/~1api~1v1~1auth~1signup/post/responses"
        );

        JsonNode login = spec.at(
                "/paths/~1api~1v1~1auth~1login/post/responses"
        );

        assertThat(signup.has("201")).isTrue();
        assertThat(signup.has("200")).isFalse();

        // 회원가입 성공 응답 예시
        JsonNode signupExample = example(signup.get("201"));

        assertThat(signupExample.at("/isSuccess").asBoolean())
                .isTrue();

        assertThat(signupExample.at("/code").asText())
                .isEqualTo(AuthSuccessCode.SIGNUP_SUCCESS.getCode());

        assertThat(signupExample.at("/message").asText())
                .isEqualTo(AuthSuccessCode.SIGNUP_SUCCESS.getMessage());

        assertThat(signupExample.at("/timestamp").asText())
                .isNotBlank();

        assertThat(signupExample.at("/result/accessToken").asText())
                .isNotBlank();

        // 로그인 성공 응답 예시
        JsonNode loginExample = example(login.get("200"));

        assertThat(loginExample.at("/isSuccess").asBoolean())
                .isTrue();

        assertThat(loginExample.at("/code").asText())
                .isEqualTo(AuthSuccessCode.LOGIN_SUCCESS.getCode());

        assertThat(loginExample.at("/message").asText())
                .isEqualTo(AuthSuccessCode.LOGIN_SUCCESS.getMessage());

        // 인증 실패 응답 예시
        JsonNode invalidCredentials = example(login.get("401"));

        assertThat(invalidCredentials.at("/isSuccess").asBoolean())
                .isFalse();

        assertThat(invalidCredentials.at("/code").asText())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS.getCode());

        assertThat(invalidCredentials.at("/message").asText())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS.getMessage());

        assertThat(example(login.get("403")).at("/code").asText())
                .isEqualTo(AuthErrorCode.ACCOUNT_DISABLED.getCode());

        assertThat(example(login.get("403")).at("/message").asText())
                .isEqualTo(AuthErrorCode.ACCOUNT_DISABLED.getMessage());

        assertThat(
                signup.at(
                        "/409/content/application~1json/examples/"
                                + "EMAIL_ALREADY_EXISTS/value/code"
                ).asText()
        ).isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS.getCode());

        assertThat(
                signup.at(
                        "/409/content/application~1json/examples/"
                                + "EMAIL_ALREADY_EXISTS/value/message"
                ).asText()
        ).isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS.getMessage());

        assertThat(
                signup.at(
                        "/400/content/application~1json/examples/"
                                + "INVALID_REQUEST/value/code"
                ).asText()
        ).isEqualTo(GlobalErrorCode.INVALID_REQUEST.getCode());

        assertThat(
                signup.at(
                        "/400/content/application~1json/examples/"
                                + "INVALID_REQUEST/value/message"
                ).asText()
        ).isEqualTo(GlobalErrorCode.INVALID_REQUEST.getMessage());

        // 성공 응답의 result가 AuthResponse DTO를 참조하는지 확인
        String schemaRef = signup.at(
                "/201/content/application~1json/schema/$ref"
        ).asText();

        assertThat(schemaRef)
                .startsWith("#/components/schemas/");

        JsonNode schema = spec.at(
                schemaRef.substring(1)
        );

        assertThat(schema.at("/properties/result/$ref").asText())
                .isEqualTo("#/components/schemas/AuthResponse");

        // 비밀번호 관련 Swagger 문서 규칙 확인
        assertThat(
                spec.at(
                        "/components/schemas/SignupRequest/"
                                + "properties/password/description"
                ).asText()
        ).contains("72바이트");

        assertThat(
                spec.at(
                        "/components/schemas/SignupRequest/"
                                + "properties/password/writeOnly"
                ).asBoolean()
        ).isTrue();
    }

    @Test
    void documentedRequestWorksAndValidationStillAppliesThroughInterface()
            throws Exception {

        JsonNode schema = spec().at(
                "/components/schemas/SignupRequest/properties"
        );

        String email = UUID.randomUUID() + "@example.com";

        var body = mapper.createObjectNode()
                .put("email", email)
                .put(
                        "password",
                        schema.at("/password/example").asText()
                );

        // 회원가입
        mvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON201"))
                .andExpect(jsonPath("$.result.accessToken").isString())
                .andExpect(jsonPath("$.timestamp").isString());

        // 로그인
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(
                        jsonPath("$.result.tokenType").value("Bearer")
                );

        // 잘못된 비밀번호 규칙
        body.put("password", "abcdefgh");

        mvc.perform(
                        post("/api/v1/auth/signup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsString(body))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON400"));

        // 인증되지 않은 요청
        mvc.perform(get("/api/v1/protected"))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/v3/api-docs"))
                .andExpect(status().isUnauthorized());

        mvc.perform(
                        post("/internal/ai/research-tasks/1/result")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isUnauthorized());
    }

    private JsonNode spec() throws Exception {

        var result = mvc.perform(
                        get("/v3/api-docs")
                )
                .andExpect(status().isOk())
                .andReturn();

        return mapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private JsonNode example(JsonNode response) {

        JsonNode media = response.at(
                "/content/application~1json"
        );

        if (media.has("example")) {
            return media.get("example");
        }

        return media.get("examples")
                .properties()
                .iterator()
                .next()
                .getValue()
                .get("value");
    }
}
