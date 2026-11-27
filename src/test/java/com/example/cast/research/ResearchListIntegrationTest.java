package com.example.cast.research;

import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.auth.service.AuthService;
import com.example.cast.persistence.TestDatabaseSupport;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class ResearchListIntegrationTest extends TestDatabaseSupport {

    private static final String URL = "/api/research-tasks";

    @Autowired MockMvc mvc;
    @Autowired AuthService auth;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;

    @Test
    void emptyListUsesDefaultPagination() throws Exception {
        var user = signup();

        mvc.perform(get(URL).header("Authorization", bearer(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.items").isEmpty())
                .andExpect(jsonPath("$.result.page").value(0))
                .andExpect(jsonPath("$.result.size").value(20))
                .andExpect(jsonPath("$.result.totalElements").value(0))
                .andExpect(jsonPath("$.result.totalPages").value(0))
                .andExpect(jsonPath("$.result.hasNext").value(false));
    }

    @Test
    void listIsOwnerScopedAndUsesStablePagination() throws Exception {
        var owner = signup();
        var other = signup();

        long first = task(owner, "first question", "COMPLETED",
                "2026-11-01T00:00:00Z");
        long second = task(owner, "second question", "RUNNING",
                "2026-11-02T00:00:00Z");
        long third = task(owner, "third question", "FAILED",
                "2026-11-02T00:00:00Z");
        long foreign = task(other, "private question", "COMPLETED",
                "2026-11-03T00:00:00Z");

        JsonNode page0 = fetch(owner, 0, 2);
        assertThat(page0.path("totalElements").asLong()).isEqualTo(3);
        assertThat(page0.path("totalPages").asInt()).isEqualTo(2);
        assertThat(page0.path("hasNext").asBoolean()).isTrue();
        assertThat(page0.path("items").size()).isEqualTo(2);
        assertThat(page0.path("items").get(0).path("taskId").asLong())
                .isEqualTo(third);
        assertThat(page0.path("items").get(0).path("question").asString())
                .isEqualTo("third question");
        assertThat(page0.path("items").get(0).path("researchTitle").asString())
                .isEqualTo("third question");
        assertThat(page0.path("items").get(0).path("status").asString())
                .isEqualTo("FAILED");
        assertThat(page0.path("items").get(0).path("createdAt").isString())
                .isTrue();
        assertThat(page0.path("items").get(1).path("taskId").asLong())
                .isEqualTo(second);

        JsonNode page1 = fetch(owner, 1, 2);
        assertThat(page1.path("items").size()).isEqualTo(1);
        assertThat(page1.path("items").get(0).path("taskId").asLong())
                .isEqualTo(first);
        assertThat(page1.path("hasNext").asBoolean()).isFalse();

        JsonNode beyond = fetch(owner, 2, 2);
        assertThat(beyond.path("items").size()).isZero();
        assertThat(beyond.path("totalElements").asLong()).isEqualTo(3);

        JsonNode otherPage = fetch(other, 0, 20);
        assertThat(otherPage.path("totalElements").asLong()).isEqualTo(1);
        assertThat(otherPage.path("items").get(0).path("taskId").asLong())
                .isEqualTo(foreign);
    }

    @Test
    void invalidPaginationReturns400() throws Exception {
        var user = signup();

        for (String[] param : new String[][] {
                {"page", "-1"},
                {"size", "0"},
                {"size", "101"}
        }) {
            mvc.perform(get(URL)
                            .header("Authorization", bearer(user))
                            .param(param[0], param[1]))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON400"));
        }
    }

    @Test
    void listRequiresAuthentication() throws Exception {
        mvc.perform(get(URL))
                .andExpect(status().isUnauthorized());
        mvc.perform(get(URL).header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resultExposesQuestionAndPreservesOwnerSecurity() throws Exception {
        var owner = signup();
        var other = signup();

        long id = task(owner, "original question", "RUNNING",
                "2026-11-01T00:00:00Z");

        mvc.perform(get(URL + "/{taskId}", id)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.taskId").value(id))
                .andExpect(jsonPath("$.result.queryId").isNumber())
                .andExpect(jsonPath("$.result.status").value("RUNNING"))
                .andExpect(jsonPath("$.result.question").value("original question"))
                .andExpect(jsonPath("$.result.researchTitle").value("original question"));

        mvc.perform(get(URL + "/{taskId}", id)
                        .header("Authorization", bearer(other)))
                .andExpect(status().isForbidden());
    }

    @Test
    void completedResultPreservesExistingFieldsAndExposesQuestion() throws Exception {
        var owner = signup();
        long id = task(owner, "completed question", "COMPLETED",
                "2026-11-01T00:00:00Z");

        jdbc.update("""
                INSERT INTO backend.answer
                    (task_id, answer_content, summary_content, answer_status)
                VALUES (?, ?, ?, 'FINAL')
                """, id, "final answer", "final summary");

        jdbc.update("""
                UPDATE backend.research_task
                SET current_step = 6, preview_summary = ?
                WHERE task_id = ?
                """, "preview summary", id);

        mvc.perform(get(URL + "/{taskId}", id)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.code").value("COMMON200"))
                .andExpect(jsonPath("$.result.taskId").value(id))
                .andExpect(jsonPath("$.result.queryId").isNumber())
                .andExpect(jsonPath("$.result.status").value("COMPLETED"))
                .andExpect(jsonPath("$.result.currentStep").value(6))
                .andExpect(jsonPath("$.result.previewSummary").value("preview summary"))
                .andExpect(jsonPath("$.result.answer").value("final answer"))
                .andExpect(jsonPath("$.result.summary").value("final summary"))
                .andExpect(jsonPath("$.result.question").value("completed question"))
                .andExpect(jsonPath("$.result.researchTitle").value("completed question"));
    }


    @Test
    void newResearchPersistsGeneratedTitle() throws Exception {
        var owner = signup();
        String question = "Youth rent support?";
        String expectedTitle = "Youth rent support";

        var body = mapper.createObjectNode()
                .put("question", question);

        var response = mvc.perform(post(URL)
                        .header("Authorization", bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().is2xxSuccessful())
                .andReturn();

        long taskId = mapper.readTree(response.getResponse()
                .getContentAsByteArray())
                .path("result").path("taskId").asLong();

        assertThat(taskId).isPositive();

        assertThat(storedTitle(taskId))
                .isEqualTo(expectedTitle);

        mvc.perform(get(URL + "/{taskId}", taskId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.question").value(question))
                .andExpect(jsonPath("$.result.researchTitle").value(expectedTitle));

        JsonNode page = fetch(owner, 0, 20);

        assertThat(page.path("items").size()).isEqualTo(1);
        assertThat(page.path("items").get(0)
                .path("researchTitle").asString())
                .isEqualTo(expectedTitle);
    }

    @Test
    void legacyTitleFallbackDoesNotModifyDatabase() throws Exception {
        var owner = signup();

        long id = task(owner, "Legacy question?", "COMPLETED",
                "2026-11-01T00:00:00Z");

        assertThat(storedTitle(id)).isNull();

        mvc.perform(get(URL + "/{taskId}", id)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.researchTitle")
                        .value("Legacy question"));

        JsonNode page = fetch(owner, 0, 20);

        assertThat(page.path("items").get(0)
                .path("researchTitle").asString())
                .isEqualTo("Legacy question");

        assertThat(storedTitle(id)).isNull();
    }

    @Test
    void resultAndListPreserveStoredTitle() throws Exception {
        var owner = signup();

        long id = task(owner, "Original question?", "COMPLETED",
                "2026-11-01T00:00:00Z");

        jdbc.update("""
                UPDATE backend.research_query
                SET research_title = ?
                WHERE query_id = (
                    SELECT query_id FROM backend.research_task
                    WHERE task_id = ?
                )
                """, "Custom research title", id);

        mvc.perform(get(URL + "/{taskId}", id)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.question")
                        .value("Original question?"))
                .andExpect(jsonPath("$.result.researchTitle")
                        .value("Custom research title"));

        JsonNode page = fetch(owner, 0, 20);

        assertThat(page.path("items").get(0)
                .path("researchTitle").asString())
                .isEqualTo("Custom research title");

        assertThat(storedTitle(id))
                .isEqualTo("Custom research title");
    }

    private String storedTitle(long taskId) {
        return jdbc.queryForObject("""
                SELECT q.research_title
                FROM backend.research_query q
                JOIN backend.research_task t
                    ON t.query_id = q.query_id
                WHERE t.task_id = ?
                """, String.class, taskId);
    }

    private AuthResponse signup() {
        return auth.signup(new SignupRequest(
                UUID.randomUUID() + "@example.invalid",
                "SecurePassword123!"));
    }

    private String bearer(AuthResponse user) {
        return "Bearer " + user.accessToken();
    }

    private JsonNode fetch(AuthResponse user, int page, int size)
            throws Exception {
        var response = mvc.perform(get(URL)
                        .header("Authorization", bearer(user))
                        .param("page", String.valueOf(page))
                        .param("size", String.valueOf(size)))
                .andExpect(status().isOk())
                .andReturn();

        return mapper.readTree(response.getResponse()
                .getContentAsByteArray()).path("result");
    }

    private long task(AuthResponse user, String question,
                      String status, String createdAt) {
        long queryId = jdbc.queryForObject(
                "INSERT INTO backend.research_query(user_id,question) "
                + "VALUES (?,?) RETURNING query_id",
                Long.class, user.userId(), question);

        var timestamp = Timestamp.from(Instant.parse(createdAt));

        return jdbc.queryForObject(
                "INSERT INTO backend.research_task"
                + "(query_id,status,created_at,updated_at) "
                + "VALUES (?,?,?,?) RETURNING task_id",
                Long.class, queryId, status, timestamp, timestamp);
    }
}
