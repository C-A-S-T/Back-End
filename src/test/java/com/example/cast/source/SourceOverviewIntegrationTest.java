package com.example.cast.source;

import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.service.AuthService;
import com.example.cast.persistence.TestDatabaseSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SourceOverviewIntegrationTest extends TestDatabaseSupport {

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired ObjectMapper mapper;

    @Test
    void newUserOverviewReturnsThreeEmptyFolders() throws Exception {
        var account = auth.signup(new SignupRequest(
                UUID.randomUUID() + "@example.invalid",
                "SecurePassword123!"
        ));

        var response = mvc.perform(get("/api/v1/sources/overview")
                        .header("Authorization", "Bearer " + account.accessToken())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn();

        var body = mapper.readTree(response.getResponse().getContentAsByteArray());
        assertThat(body.path("isSuccess").asBoolean()).isTrue();
        assertThat(body.path("code").asText()).isEqualTo("COMMON200");

        var result = body.path("result");
        assertThat(result.path("totalSavedDocumentCount").asLong()).isZero();
        assertThat(result.path("autoSavedDocumentCount").asLong()).isZero();
        assertThat(result.path("unclassifiedDocumentCount").asLong()).isZero();
        assertThat(result.path("legacyFolderDocumentCount").asLong()).isZero();
        assertThat(result.path("folderCount").asInt()).isEqualTo(3);

        // 스크랩 기능은 지원하고, 미구현 정책 변경 집계만 null로 남긴다.
        assertThat(result.path("scrappedSentenceCount").asLong()).isZero();
        assertThat(result.has("changedPolicyCount")).isTrue();
        assertThat(result.get("changedPolicyCount").isNull()).isTrue();

        var folders = result.path("folders");
        assertThat(folders.isArray()).isTrue();
        assertThat(folders.size()).isEqualTo(3);

        assertThat(folders.get(0).path("category").asText()).isEqualTo("HOUSING");
        assertThat(folders.get(0).path("folderName").asText())
                .isEqualTo("\uCCAD\uC57D\u00B7\uC8FC\uAC70");
        assertThat(folders.get(1).path("category").asText()).isEqualTo("GOV_SUPPORT");
        assertThat(folders.get(1).path("folderName").asText())
                .isEqualTo("\uC815\uBD80 \uC9C0\uC6D0\uC815\uCC45");
        assertThat(folders.get(2).path("category").asText()).isEqualTo("EDUCATION");
        assertThat(folders.get(2).path("folderName").asText())
                .isEqualTo("\uAD50\uC721");

        for (var folder : folders) {
            assertThat(folder.path("folderId").asLong()).isPositive();
            assertThat(folder.path("documentCount").asLong()).isZero();
        }
    }
}
