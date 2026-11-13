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

/** 실제 문서 생성과 Swagger 리소스 접근, 인터페이스로 분리한 HTTP 계약을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SwaggerIntegrationTest extends TestDatabaseSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test void swaggerUiAndConfigurationArePublic() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Swagger UI")));
        mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("/v3/api-docs"))
                .andExpect(jsonPath("$.persistAuthorization").value(false));
    }

    @Test void specDocumentsOnlyAuthAndDoesNotRequireTokenForSignupOrLogin() throws Exception {
        JsonNode spec = spec();
        assertThat(spec.get("paths").size()).isEqualTo(2);
        assertThat(spec.at("/paths/~1api~1v1~1auth~1signup/post/security").isArray()).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1auth~1signup/post/security").size()).isZero();
        assertThat(spec.at("/paths/~1api~1v1~1auth~1login/post/security").isArray()).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1auth~1login/post/security").size()).isZero();
        assertThat(spec.at("/components/securitySchemes/bearerAuth/type").asText()).isEqualTo("http");
        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.at("/servers/0/url").asText()).isEqualTo("/");
    }

    @Test void responseExamplesAgreeWithActualCodesAndSuccessHasTypedData() throws Exception {
        JsonNode spec = spec();
        JsonNode signup = spec.at("/paths/~1api~1v1~1auth~1signup/post/responses");
        JsonNode login = spec.at("/paths/~1api~1v1~1auth~1login/post/responses");
        assertThat(signup.has("201")).isTrue();
        assertThat(signup.has("200")).isFalse();
        assertThat(example(signup.get("201")).at("/result/message").asText()).isEqualTo(AuthSuccessCode.SIGNUP_SUCCESS.getMessage());
        assertThat(example(login.get("200")).at("/result/message").asText()).isEqualTo(AuthSuccessCode.LOGIN_SUCCESS.getMessage());
        assertThat(example(login.get("401")).at("/result/message").asText()).isEqualTo(AuthErrorCode.INVALID_CREDENTIALS.getMessage());
        assertThat(example(login.get("403")).at("/result/message").asText()).isEqualTo(AuthErrorCode.ACCOUNT_DISABLED.getMessage());
        assertThat(signup.at("/409/content/application~1json/examples/EMAIL_ALREADY_EXISTS/value/result/message").asText())
                .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS.getMessage());
        assertThat(signup.at("/400/content/application~1json/examples/INVALID_REQUEST/value/result/message").asText())
                .isEqualTo(GlobalErrorCode.INVALID_REQUEST.getMessage());
        String schemaRef = signup.at("/201/content/application~1json/schema/$ref").asText();
        assertThat(schemaRef).startsWith("#/components/schemas/");
        JsonNode schema = spec.at(schemaRef.substring(1));
        assertThat(schema.at("/properties/data/$ref").asText()).isEqualTo("#/components/schemas/AuthResponse");
        assertThat(spec.at("/components/schemas/SignupRequest/properties/password/description").asText()).contains("72바이트");
        assertThat(spec.at("/components/schemas/SignupRequest/properties/password/writeOnly").asBoolean()).isTrue();
    }

    @Test void documentedRequestWorksAndValidationStillAppliesThroughInterface() throws Exception {
        JsonNode schema = spec().at("/components/schemas/SignupRequest/properties");
        String email = UUID.randomUUID() + "@example.com";
        var body = mapper.createObjectNode().put("email", email)
                .put("password", schema.at("/password/example").asText());
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.accessToken").isString());
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.tokenType").value("Bearer"));
        body.put("password", "abcdefgh");
        mvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/protected")).andExpect(status().isUnauthorized());
        mvc.perform(post("/v3/api-docs")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/ai/research-tasks/1/result").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode spec() throws Exception {
        var result = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        return mapper.readTree(result.getResponse().getContentAsByteArray());
    }

    private JsonNode example(JsonNode response) {
        JsonNode media = response.at("/content/application~1json");
        if (media.has("example")) return media.get("example");
        return media.get("examples").properties().iterator().next().getValue().get("value");
    }
}
