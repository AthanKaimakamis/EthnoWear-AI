package fmi.ethnowear.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.auth.AdminAuthController;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentProcessingController;
import fmi.ethnowear.api.exception.AdminAuthExceptionHandler;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.service.auth.AdminAuthenticationService;
import fmi.ethnowear.application.service.auth.CurrentUserService;
import fmi.ethnowear.application.service.auth.EthnoWearUserPrincipal;
import fmi.ethnowear.application.service.auth.JwtUserStateValidator;
import fmi.ethnowear.application.service.auth.JwtTokenService;
import fmi.ethnowear.application.service.auth.LoginAttemptService;
import fmi.ethnowear.application.service.auth.PasswordChangeService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingRequestService;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.user.RoleName;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentEndpointSecurityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private AnnotationConfigWebApplicationContext context;
    private StubProcessingRequestService processingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        TestPropertyValues.of().applyTo(context);
        context.register(
                SecurityConfig.class,
                JwtSecurityTestConfiguration.class
        );
        context.refresh();

        processingService = context.getBean(StubProcessingRequestService.class);
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
                        .springSecurity(context.getBean(FilterChainProxy.class)))
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void rejectsUnauthenticatedAdminRequests() throws Exception {
        mockMvc.perform(post("/api/admin/document-pages/11/ocr"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void issuesBearerTokenAndAllowsAdministratorRequest() throws Exception {
        String token = login("admin", "secret");

        mockMvc.perform(post("/api/admin/document-pages/11/ocr")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isAccepted());
        assertEquals(11L, processingService.pageId);
    }

    @Test
    void allowsEditorContentMutationsButRejectsReviewer() throws Exception {
        mockMvc.perform(post("/api/admin/document-pages/11/ocr")
                        .header("Authorization", "Bearer " + validToken("EDITOR")))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/admin/document-pages/11/ocr")
                        .header("Authorization", "Bearer " + validToken("REVIEWER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsReviewerDecisionsButRejectsEditor() throws Exception {
        mockMvc.perform(post("/api/admin/document-pages/11/approve")
                        .header("Authorization", "Bearer " + validToken("REVIEWER")))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/document-pages/11/approve")
                        .header("Authorization", "Bearer " + validToken("EDITOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminReadsForEveryManagementRole() throws Exception {
        for (String role : List.of("ADMINISTRATOR", "REVIEWER", "EDITOR")) {
            mockMvc.perform(get("/api/admin/security-check")
                            .header("Authorization", "Bearer " + validToken(role)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void reservesUserManagementForAdministrators() throws Exception {
        mockMvc.perform(get("/api/admin/users/security-check")
                        .header("Authorization", "Bearer " + validToken("ADMINISTRATOR")))
                .andExpect(status().isOk());

        for (String role : List.of("REVIEWER", "EDITOR")) {
            mockMvc.perform(get("/api/admin/users/security-check")
                            .header("Authorization", "Bearer " + validToken(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void deniesUnclassifiedAdminMutations() throws Exception {
        mockMvc.perform(post("/api/admin/unclassified")
                        .header(
                                "Authorization",
                                "Bearer " + validToken("ADMINISTRATOR")
                        ))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsInvalidCredentialsWithoutReturningToken() throws Exception {
        mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message")
                        .value("Invalid administrator credentials"))
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void rejectsMalformedExpiredAndNonAdminTokens() throws Exception {
        mockMvc.perform(post("/api/admin/document-pages/11/ocr")
                        .header("Authorization", "Bearer malformed"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/document-pages/11/ocr")
                        .header("Authorization", "Bearer " + token(
                                Instant.now().minusSeconds(120),
                                List.of("ADMINISTRATOR")
                        )))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/admin/document-pages/11/ocr")
                        .header("Authorization", "Bearer " + token(
                                Instant.now().plusSeconds(120),
                                List.of("USER")
                        )))
                .andExpect(status().isForbidden());
    }

    @Test
    void leavesPublicEndpointsOpenWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/public/security-check"))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(Map.of(
                                "username", username,
                                "password", password
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode response = objectMapper.readTree(body);

        return response.get("accessToken").asText();
    }

    private String token(Instant expiresAt, List<String> roles) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ethnowear-api")
                .subject("test-user")
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .claim("userId", 1L)
                .claim("tokenVersion", 0)
                .claim("mustChangePassword", false)
                .claim("roles", roles)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .build();

        return context.getBean(JwtEncoder.class)
                .encode(JwtEncoderParameters.from(header, claims))
                .getTokenValue();
    }

    private String validToken(String role) {
        return token(
                Instant.now().plusSeconds(60),
                List.of(role)
        );
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    static class JwtSecurityTestConfiguration {

        @Bean
        JwtProperties jwtProperties() {
            return new JwtProperties(
                    "test-jwt-secret-with-at-least-32-bytes",
                    "ethnowear-api",
                    Duration.ofMinutes(15)
            );
        }

        @Bean
        JwtTokenService jwtTokenService(
                JwtEncoder encoder,
                JwtProperties properties,
                Clock clock
        ) {
            return new JwtTokenService(encoder, properties, clock);
        }

        @Bean
        AdminAuthenticationService adminAuthenticationService(
                AuthenticationManager authenticationManager,
                JwtTokenService tokenService,
                LoginAttemptService loginAttemptService
        ) {
            return new AdminAuthenticationService(
                    authenticationManager,
                    tokenService,
                    loginAttemptService
            );
        }

        @Bean
        AdminAuthController adminAuthController(
                AdminAuthenticationService authenticationService,
                PasswordChangeService passwordChangeService,
                CurrentUserService currentUserService
        ) {
            return new AdminAuthController(
                    authenticationService,
                    passwordChangeService,
                    currentUserService
            );
        }

        @Bean
        UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
            EthnoWearUserPrincipal principal = EthnoWearUserPrincipal.of(
                    1L,
                    "admin",
                    passwordEncoder.encode("secret"),
                    true,
                    null,
                    false,
                    null,
                    0,
                    java.util.Set.of(RoleName.ADMINISTRATOR),
                    java.time.LocalDateTime.now()
            );

            return username -> principal;
        }

        @Bean
        JwtUserStateValidator jwtUserStateValidator() {
            return new JwtUserStateValidator(null, null) {
                @Override
                public OAuth2TokenValidatorResult validate(
                        org.springframework.security.oauth2.jwt.Jwt jwt
                ) {
                    return OAuth2TokenValidatorResult.success();
                }
            };
        }

        @Bean
        LoginAttemptService loginAttemptService() {
            return new LoginAttemptService(null, null) {
                @Override
                public void recordSuccess(Long userId) {
                }

                @Override
                public void recordFailure(String username) {
                }
            };
        }

        @Bean
        PasswordChangeService passwordChangeService() {
            return new PasswordChangeService(null, null, null);
        }

        @Bean
        CurrentUserService currentUserService() {
            return new CurrentUserService(null, null, null);
        }

        @Bean
        AdminAuthExceptionHandler adminAuthExceptionHandler() {
            return new AdminAuthExceptionHandler();
        }

        @Bean
        StubProcessingRequestService processingRequestService() {
            return new StubProcessingRequestService();
        }

        @Bean
        AdminDocumentProcessingController processingController(
                StubProcessingRequestService processingRequestService
        ) {
            return new AdminDocumentProcessingController(processingRequestService);
        }

        @Bean
        PublicSecurityController publicSecurityController() {
            return new PublicSecurityController();
        }

        @Bean
        AdminSecurityController adminSecurityController() {
            return new AdminSecurityController();
        }
    }

    @RestController
    static class PublicSecurityController {

        @GetMapping("/api/public/security-check")
        String check() {
            return "ok";
        }
    }

    @RestController
    static class AdminSecurityController {

        @GetMapping("/api/admin/security-check")
        String check() {
            return "ok";
        }

        @GetMapping("/api/admin/users/security-check")
        String users() {
            return "ok";
        }

        @PostMapping(
                "/api/admin/document-pages/{pageId}/approve"
        )
        String approve() {
            return "ok";
        }

        @PostMapping(
                "/api/admin/unclassified"
        )
        String unclassified() {
            return "ok";
        }
    }

    private static final class StubProcessingRequestService
            extends DocumentProcessingRequestService {

        private Long pageId;

        private StubProcessingRequestService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public DocumentProcessingJobDetails requestOcr(Long pageId) {
            this.pageId = pageId;

            return new DocumentProcessingJobDetails(
                    81L, JobType.OCR, JobStatus.QUEUED, 41L, pageId, 31L,
                    null, 0, 0, 3, null, null, null, null, null, null,
                    null, null, null, null, null, null, null, null
            );
        }
    }
}
