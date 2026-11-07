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
/** 테스트 전용 컨트롤러로 필터부터 예외 처리까지 실제 HTTP 계약을 검증한다. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(ApiContractIntegrationTest.ProbeController.class)
class ApiContractIntegrationTest extends TestDatabaseSupport {
    @Autowired MockMvc mvc;
    @org.springframework.boot.test.context.TestComponent
    @RestController
    static class ProbeController {
        record Input(@NotBlank String title) {}
        @PostMapping("/test-contract/input")
        ResponseEntity<ApiResponse<Input>> input(@Valid @RequestBody Input input) {
            return ApiResponse.success(DocumentSuccessCode.DOCUMENT_CREATED, input);
        }
        @GetMapping("/test-contract/business")
        void business() { throw new BusinessException(GlobalErrorCode.DATA_CONFLICT); }
        @GetMapping("/test-contract/failure")
        void failure() { throw new IllegalStateException("internal-secret-must-not-leak"); }
    }
    @Test void anonymousApiGetsCommon401() throws Exception {
        mvc.perform(get("/test-contract/business").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.result.code").value(401))
                .andExpect(jsonPath("$.result.success").value(false));
    }
    @Test void browserKeepsLoginRedirect() throws Exception {
        mvc.perform(get("/test-contract/business").accept(MediaType.TEXT_HTML))
                .andExpect(status().isFound()).andExpect(redirectedUrlPattern("**/login"));
    }
    @Test void badBasicCredentialsGetCommon401() throws Exception {
        mvc.perform(get("/test-contract/business").with(httpBasic("invalid", "invalid")))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.result.code").value(401));
    }
    @Test void missingCsrfGetsCommon403() throws Exception {
        mvc.perform(post("/test-contract/input").with(user("tester"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"value\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.result.code").value(403));
    }
    @Test void validInputGetsCommonSuccess() throws Exception {
        mvc.perform(post("/test-contract/input").with(user("tester")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"value\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.result.code").value(201))
                .andExpect(jsonPath("$.data.title").value("value"));
    }
    @Test void invalidInputGetsCommon400() throws Exception {
        mvc.perform(post("/test-contract/input").with(user("tester")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"title\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.result.code").value(400));
    }
    @Test void malformedJsonGetsCommon400() throws Exception {
        mvc.perform(post("/test-contract/input").with(user("tester")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.result.code").value(400));
    }
    @Test void missingRouteGetsCommon404() throws Exception {
        mvc.perform(get("/test-contract/missing").with(user("tester")))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.result.code").value(404));
    }
    @Test void wrongMethodGetsCommon405() throws Exception {
        mvc.perform(get("/test-contract/input").with(user("tester")))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.result.code").value(405));
    }
    @Test void businessFailureGetsCommon409() throws Exception {
        mvc.perform(get("/test-contract/business").with(user("tester")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.result.code").value(409));
    }
    @Test void internalFailureIsRedacted() throws Exception {
        mvc.perform(get("/test-contract/failure").with(user("tester")))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.result.code").value(500))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("internal-secret"))));
    }
    @Test void healthRemainsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
