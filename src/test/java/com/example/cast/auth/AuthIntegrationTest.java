package com.example.cast.auth;

import com.example.cast.auth.code.AuthErrorCode;
import com.example.cast.auth.code.AuthSuccessCode;
import com.example.cast.auth.dto.request.SignupRequest;
import com.example.cast.auth.dto.response.AuthResponse;
import com.example.cast.auth.security.AuthenticatedUser;
import com.example.cast.auth.service.AuthService;
import com.example.cast.global.code.GlobalErrorCode;
import com.example.cast.global.exception.BusinessException;
import com.example.cast.global.response.ApiResponse;
import com.example.cast.persistence.TestDatabaseSupport;
import com.example.cast.user.dto.request.UserInputs;
import com.example.cast.user.entity.AccountStatus;
import com.example.cast.user.repository.UserRepository;
import com.example.cast.user.service.UserWriteService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * PostgreSQL 제약 및 Security 필터를 포함한 인증 API 계약 검증.
 * 실행은 별도 승인 후에만 한다.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(AuthIntegrationTest.IdentityProbe.class)
class AuthIntegrationTest extends TestDatabaseSupport {

    private static final String PASSWORD = "SecurePassword123!";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired AuthService auth;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserWriteService userWriter;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtDecoder decoder;
    @Autowired JwtEncoder encoder;

    /**
     * 요청 ID가 아닌 인증 principal을 사용하는 방법을 검증한다.
     */
    @TestComponent
    @RestController
    static class IdentityProbe {

        @GetMapping("/test-auth/users/{ownerId}")
        ResponseEntity<ApiResponse<Long>> owner(
                @AuthenticationPrincipal AuthenticatedUser principal,
                @PathVariable Long ownerId
        ) {
            if (!principal.userId().equals(ownerId)) {
                throw new BusinessException(GlobalErrorCode.FORBIDDEN);
            }

            return ApiResponse.success(
                    AuthSuccessCode.LOGIN_SUCCESS,
                    principal.userId()
            );
        }
    }

    @Test
    void signupCreatesThreeDefaultSourceFolders() throws Exception {
        MvcResult result = credentials("signup", uniqueEmail(), PASSWORD);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);

        long userId = body(result).get("result").get("userId").asLong();
        List<String> folders = jdbcTemplate.queryForList(
                """
                SELECT folder_category || ':' || folder_name
                FROM backend.source_folder
                WHERE user_id = ?
                """,
                String.class,
                userId
        );

        assertThat(folders).hasSize(3)
                .containsExactlyInAnyOrder(
                        "HOUSING:\uCCAD\uC57D\u00B7\uC8FC\uAC70",
                        "GOV_SUPPORT:\uC815\uBD80 \uC9C0\uC6D0\uC815\uCC45",
                        "EDUCATION:\uAD50\uC721"
                );
    }

    @Test
    void signupNormalizesHashesAndIssuesTokenWithoutSession()
            throws Exception {

        String email = uniqueEmail();

        MvcResult result = credentials(
                "signup",
                "  " + email.toUpperCase(Locale.ROOT) + "  ",
                PASSWORD
        );

        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        assertThat(result.getRequest().getSession(false)).isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).isNull();

        JsonNode response = body(result);

        assertThat(response.get("isSuccess").asBoolean()).isTrue();
        assertThat(response.get("code").asText())
                .isEqualTo(AuthSuccessCode.SIGNUP_SUCCESS.getCode());
        assertThat(response.get("message").asText())
                .isEqualTo(AuthSuccessCode.SIGNUP_SUCCESS.getMessage());

        assertThat(LocalDateTime.parse(
                response.get("timestamp").asText()
        )).isNotNull();

        // 실제 데이터는 최상위 result에 위치한다.
        JsonNode data = response.get("result");

        var user = users.findById(
                data.get("userId").asLong()
        ).orElseThrow();

        assertThat(user.getEmail()).isEqualTo(email);
        assertThat(user.getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);

        assertThat(user.getPasswordHash())
                .startsWith("$2a$10$")
                .isNotEqualTo(PASSWORD);

        assertThat(passwords.matches(
                PASSWORD,
                user.getPasswordHash()
        )).isTrue();

        assertThat(data.get("tokenType").asText())
                .isEqualTo("Bearer");

        assertThat(data.get("expiresIn").asLong())
                .isEqualTo(3600);

        Jwt jwt = decoder.decode(
                data.get("accessToken").asText()
        );

        assertThat(jwt.getSubject())
                .isEqualTo(user.getUserId().toString());

        assertThat(jwt.getClaimAsString("iss"))
                .isEqualTo("cast-api");

        assertThat(jwt.getHeaders().get("alg"))
                .isEqualTo("HS256");

        assertThat(
                jwt.getExpiresAt().getEpochSecond()
                        - jwt.getIssuedAt().getEpochSecond()
        ).isEqualTo(3600);

        assertThat(jwt.getClaims())
                .containsOnlyKeys("iss", "sub", "iat", "exp");

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(PASSWORD, user.getPasswordHash());

        assertThat(
                body(credentials(
                        "login",
                        email.toUpperCase(Locale.ROOT),
                        PASSWORD
                )).get("result").get("userId").asLong()
        ).isEqualTo(user.getUserId());

        mvc.perform(
                        get("/test-auth/users/" + user.getUserId())
                                .header(
                                        "Authorization",
                                        "Bearer " + data.get("accessToken").asText()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isSuccess").value(true))
                .andExpect(jsonPath("$.result").value(user.getUserId()));
    }

    @Test
    void duplicateEmailIncludesCaseVariantsAndExistingMixedCaseRows()
            throws Exception {

        var account = signup();

        for (String email : List.of(
                account.email(),
                " " + account.email().toUpperCase(Locale.ROOT) + " "
        )) {
            MvcResult result = credentials(
                    "signup",
                    email,
                    PASSWORD
            );

            assertThat(result.getResponse().getStatus())
                    .isEqualTo(409);

            JsonNode response = body(result);

            assertThat(response.get("isSuccess").asBoolean())
                    .isFalse();

            assertThat(response.get("code").asText())
                    .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS.getCode());

            assertThat(response.get("message").asText())
                    .isEqualTo(AuthErrorCode.EMAIL_ALREADY_EXISTS.getMessage());
        }

        String legacyEmail = uniqueEmail().toUpperCase(Locale.ROOT);

        userWriter.create(new UserInputs.Create(
                legacyEmail,
                passwords.encode(PASSWORD)
        ));

        assertThat(
                credentials(
                        "signup",
                        legacyEmail.toLowerCase(Locale.ROOT),
                        PASSWORD
                ).getResponse().getStatus()
        ).isEqualTo(409);

        assertThat(
                credentials(
                        "login",
                        legacyEmail.toLowerCase(Locale.ROOT),
                        PASSWORD
                ).getResponse().getStatus()
        ).isEqualTo(200);
    }

    @Test
    void legacyCaseCollisionsDoNotSelectAnArbitraryAccount()
            throws Exception {

        String email = uniqueEmail();

        userWriter.create(new UserInputs.Create(
                email,
                passwords.encode(PASSWORD)
        ));

        userWriter.create(new UserInputs.Create(
                email.toUpperCase(Locale.ROOT),
                passwords.encode(PASSWORD)
        ));

        assertThat(
                credentials("login", email, PASSWORD)
                        .getResponse().getStatus()
        ).isEqualTo(401);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "invalid",
            "user@",
            "@example.com",
            "user name@example.com",
            " ",
            ""
    })
    void malformedEmailsAreRejectedForBothEndpoints(String email)
            throws Exception {

        for (String endpoint : List.of("signup", "login")) {
            assertThat(
                    credentials(endpoint, email, PASSWORD)
                            .getResponse().getStatus()
            ).isEqualTo(400);
        }
    }

    static Stream<String> invalidSignupPasswords() {
        return Stream.of(
                "",
                "       ",
                "Ab1234!",
                "abcdefgh",
                "12345678",
                "!!!!!!!!",
                "Abcdef12",
                "Abcdefg!",
                "1234567!",
                "Abcdef1 ",
                "Abcdef1가",
                "Abcdef1😀",
                "Aa1!" + "a".repeat(69),
                "Aa1!" + "가".repeat(23),
                "Aa1!" + "😀".repeat(18)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidSignupPasswords")
    void passwordRulesRejectMissingCategoriesShortBlankAndOver72Utf8Bytes(
            String password
    ) throws Exception {

        assertThat(
                credentials("signup", uniqueEmail(), password)
                        .getResponse().getStatus()
        ).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ascii", "korean"})
    void exactly72Utf8BytesCanSignUpAndLogin(String kind)
            throws Exception {

        String password = "ascii".equals(kind)
                ? "Aa1!" + "a".repeat(68)
                : "Aa1!ab" + "가".repeat(22);

        String email = uniqueEmail();

        assertThat(
                credentials("signup", email, password)
                        .getResponse().getStatus()
        ).isEqualTo(201);

        assertThat(
                credentials("login", email, password)
                        .getResponse().getStatus()
        ).isEqualTo(200);

        assertThat(
                credentials("login", email, password + "a")
                        .getResponse().getStatus()
        ).isEqualTo(400);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abcdef1!",
            "ABCDEF1!",
            "가나다라마a1!",
            "Abcdef1_"
    })
    void eightCharacterPasswordsWithEveryRequiredCategoryAreAccepted(
            String password
    ) throws Exception {

        assertThat(
                credentials("signup", uniqueEmail(), password)
                        .getResponse().getStatus()
        ).isEqualTo(201);
    }

    @Test
    void existingPasswordsAreNotSubjectToNewSignupCompositionRule()
            throws Exception {

        String email = uniqueEmail();

        userWriter.create(new UserInputs.Create(
                email,
                passwords.encode("abcdefgh")
        ));

        assertThat(
                credentials("login", email, "abcdefgh")
                        .getResponse().getStatus()
        ).isEqualTo(200);
    }

    @Test
    void missingFieldsAndMalformedJsonAre400()
            throws Exception {

        for (String endpoint : List.of("signup", "login")) {
            for (String json : List.of(
                    "{}",
                    "{",
                    "null",
                    "{\"email\":null,\"password\":null}"
            )) {
                mvc.perform(
                                post("/api/v1/auth/" + endpoint)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json)
                        )
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.isSuccess").value(false))
                        .andExpect(jsonPath("$.code").value("COMMON400"));
            }
        }
    }

    @Test
    void loginSuccessAndUnknownEmailHaveNoCredentialDisclosure()
            throws Exception {

        var account = signup();

        MvcResult success = credentials(
                "login",
                account.email(),
                PASSWORD
        );

        assertThat(success.getResponse().getStatus())
                .isEqualTo(200);

        assertThat(body(success).get("message").asText())
                .isEqualTo(AuthSuccessCode.LOGIN_SUCCESS.getMessage());

        MvcResult unknown = credentials(
                "login",
                uniqueEmail(),
                PASSWORD
        );

        MvcResult wrong = credentials(
                "login",
                account.email(),
                "WrongPassword123!"
        );

        assertThat(unknown.getResponse().getStatus())
                .isEqualTo(401);

        assertThat(wrong.getResponse().getStatus())
                .isEqualTo(401);

        JsonNode unknownBody = body(unknown);
        JsonNode wrongBody = body(wrong);

        // timestamp는 요청마다 달라질 수 있으므로 제외하고 비교한다.
        for (String field : List.of(
                "isSuccess",
                "code",
                "message",
                "result"
        )) {
            assertThat(unknownBody.get(field))
                    .isEqualTo(wrongBody.get(field));
        }

        assertThat(unknownBody.get("timestamp").asText())
                .isNotBlank();

        assertThat(wrongBody.get("timestamp").asText())
                .isNotBlank();

        assertThat(wrongBody.get("code").asText())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS.getCode());

        assertThat(wrongBody.get("message").asText())
                .isEqualTo(AuthErrorCode.INVALID_CREDENTIALS.getMessage());
    }

    @ParameterizedTest
    @EnumSource(
            value = AccountStatus.class,
            names = {"SUSPENDED", "WITHDRAWN"}
    )
    void disabledAccountsRequireCorrectPasswordAndLoseExistingTokenAccess(
            AccountStatus accountStatus
    ) throws Exception {

        var account = signup();

        userWriter.changeStatus(
                account.userId(),
                new UserInputs.Status(accountStatus)
        );

        assertThat(
                credentials(
                        "login",
                        account.email(),
                        "WrongPassword123!"
                ).getResponse().getStatus()
        ).isEqualTo(401);

        MvcResult correct = credentials(
                "login",
                account.email(),
                PASSWORD
        );

        assertThat(correct.getResponse().getStatus())
                .isEqualTo(403);

        assertThat(body(correct).get("code").asText())
                .isEqualTo(AuthErrorCode.ACCOUNT_DISABLED.getCode());

        assertThat(body(correct).get("message").asText())
                .isEqualTo(AuthErrorCode.ACCOUNT_DISABLED.getMessage());

        mvc.perform(
                        get("/test-auth/users/" + account.userId())
                                .header(
                                        "Authorization",
                                        "Bearer " + account.accessToken()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH4031"));
    }

    @Test
    void authenticatedPrincipalCannotBeReplacedByRequestedOwnerId()
            throws Exception {

        var first = signup();
        var second = signup();

        mvc.perform(
                        get("/test-auth/users/" + first.userId())
                                .param("userId", second.userId().toString())
                                .header(
                                        "Authorization",
                                        "Bearer " + first.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value(first.userId()));

        mvc.perform(
                        get("/test-auth/users/" + second.userId())
                                .header(
                                        "Authorization",
                                        "Bearer " + first.accessToken()
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("COMMON403"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "expired",
            "wrong-issuer",
            "zero-subject",
            "negative-subject",
            "text-subject",
            "overflow-subject",
            "missing-subject",
            "missing-exp",
            "missing-iat",
            "future-iat",
            "missing-user"
    })
    void signedButInvalidTokensAre401(String scenario)
            throws Exception {

        var account = signup();
        Instant now = Instant.now();

        var claims = JwtClaimsSet.builder()
                .issuer("cast-api")
                .subject(account.userId().toString())
                .issuedAt(now.minusSeconds(10))
                .expiresAt(now.plusSeconds(3600));

        switch (scenario) {
            case "expired" ->
                    claims.issuedAt(now.minusSeconds(7200))
                            .expiresAt(now.minusSeconds(3600));

            case "wrong-issuer" ->
                    claims.issuer("other-api");

            case "zero-subject" ->
                    claims.subject("0");

            case "negative-subject" ->
                    claims.subject("-1");

            case "text-subject" ->
                    claims.subject("abc");

            case "overflow-subject" ->
                    claims.subject("9223372036854775808");

            case "missing-subject" ->
                    claims.claims(values -> values.remove("sub"));

            case "missing-exp" ->
                    claims.claims(values -> values.remove("exp"));

            case "missing-iat" ->
                    claims.claims(values -> values.remove("iat"));

            case "future-iat" ->
                    claims.issuedAt(now.plusSeconds(600));

            case "missing-user" ->
                    claims.subject(Long.toString(Long.MAX_VALUE));

            default -> throw new AssertionError(scenario);
        }

        String token = encoder.encode(
                JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims.build()
                )
        ).getTokenValue();

        mvc.perform(
                        get("/test-auth/users/" + account.userId())
                                .header("Authorization", "Bearer " + token)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON401"));
    }

    @Test
    void missingTamperedAndDeletedUserTokensAre401()
            throws Exception {

        var account = signup();

        String[] parts = account.accessToken().split("\\.");

        parts[2] = (
                parts[2].charAt(0) == 'A' ? "B" : "A"
        ) + parts[2].substring(1);

        mvc.perform(
                get("/test-auth/users/" + account.userId())
        ).andExpect(status().isUnauthorized());

        mvc.perform(
                get("/test-auth/users/" + account.userId())
                        .header(
                                "Authorization",
                                "Bearer " + String.join(".", parts)
                        )
        ).andExpect(status().isUnauthorized());

        // Default folders reference this user, so remove test-owned folders first.
        jdbcTemplate.update(
                "DELETE FROM backend.source_folder WHERE user_id = ?",
                account.userId()
        );
        users.deleteById(account.userId());

        mvc.perform(
                get("/test-auth/users/" + account.userId())
                        .header(
                                "Authorization",
                                "Bearer " + account.accessToken()
                        )
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void tokensAreNotAcceptedFromQueryParametersOrSessions()
            throws Exception {

        var account = signup();

        mvc.perform(
                get("/test-auth/users/" + account.userId())
                        .param("access_token", account.accessToken())
        ).andExpect(status().isUnauthorized());

        var authentication =
                org.springframework.security.authentication
                        .UsernamePasswordAuthenticationToken.authenticated(
                                new AuthenticatedUser(account.userId()),
                                null,
                                List.of()
                        );

        var context =
                new org.springframework.security.core.context
                        .SecurityContextImpl(authentication);

        mvc.perform(
                get("/test-auth/users/" + account.userId())
                        .sessionAttr("SPRING_SECURITY_CONTEXT", context)
        ).andExpect(status().isUnauthorized());
    }

    @Test
    void publicRoutesAreRestrictedToIntendedMethods()
            throws Exception {

        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());

        mvc.perform(post("/actuator/health"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/auth/signup"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void corsAllowsOnlyConfiguredFrontend()
            throws Exception {

        mvc.perform(
                        options("/api/v1/auth/signup")
                                .header("Origin", "http://localhost:5173")
                                .header("Access-Control-Request-Method", "POST")
                                .header(
                                        "Access-Control-Request-Headers",
                                        "Content-Type,Authorization"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "http://localhost:5173"
                ))
                .andExpect(header().doesNotExist(
                        "Access-Control-Allow-Credentials"
                ));

        mvc.perform(
                        options("/api/v1/auth/signup")
                                .header("Origin", "https://untrusted.example")
                                .header("Access-Control-Request-Method", "POST")
                )
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(
                        "Access-Control-Allow-Origin"
                ));
    }

    @Test
    void concurrentCaseVariantSignupCreatesOneAccount()
            throws Exception {

        String email = uniqueEmail();

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);

        try (var pool = Executors.newFixedThreadPool(2)) {

            var futures = Stream.of(
                    email,
                    email.toUpperCase(Locale.ROOT)
            ).map(candidate -> pool.submit(() -> {

                ready.countDown();

                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("start timeout");
                }

                return credentials(
                        "signup",
                        candidate,
                        PASSWORD
                );

            })).toList();

            assertThat(ready.await(10, TimeUnit.SECONDS))
                    .isTrue();

            start.countDown();

            MvcResult first = futures.get(0)
                    .get(30, TimeUnit.SECONDS);

            MvcResult second = futures.get(1)
                    .get(30, TimeUnit.SECONDS);

            assertThat(List.of(
                    first.getResponse().getStatus(),
                    second.getResponse().getStatus()
            )).containsExactlyInAnyOrder(201, 409);

            MvcResult conflict =
                    first.getResponse().getStatus() == 409
                            ? first
                            : second;

            JsonNode response = body(conflict);

            assertThat(response.get("code").asText())
                    .isEqualTo(
                            AuthErrorCode.EMAIL_ALREADY_EXISTS.getCode()
                    );

            assertThat(response.get("message").asText())
                    .isEqualTo(
                            AuthErrorCode.EMAIL_ALREADY_EXISTS.getMessage()
                    );

            assertThat(users.findAllByEmailIgnoreCase(email))
                    .hasSize(1);

        } finally {
            start.countDown();
        }
    }

    private AuthResponse signup() {
        return auth.signup(new SignupRequest(
                uniqueEmail(),
                PASSWORD
        ));
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private JsonNode body(MvcResult result) {
        return mapper.readTree(
                result.getResponse().getContentAsByteArray()
        );
    }

    private MvcResult credentials(
            String endpoint,
            String email,
            String password
    ) throws Exception {

        return mvc.perform(
                post("/api/v1/auth/" + endpoint)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(
                                Map.of(
                                        "email", email,
                                        "password", password
                                )
                        ))
        ).andReturn();
    }
}