package fmi.ethnowear.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.publicauth.PublicAuthController;
import fmi.ethnowear.api.exception.PublicAuthExceptionHandler;
import fmi.ethnowear.application.dto.publicauth.PublicUserDetails;
import fmi.ethnowear.application.model.publicauth.PublicUserPrincipal;
import fmi.ethnowear.application.service.publicauth.PublicAuthenticationService;
import fmi.ethnowear.infrastructure.security.publicauth.PublicAuthCookies;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicAuthenticationSecurityTest {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private PublicAuthenticationService service;
    private final PublicUserPrincipal principal = new PublicUserPrincipal(7, UUID.randomUUID(), 11);

    @BeforeEach
    void setup() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(SecurityConfig.class, PublicSecurityConfig.class,
                DocumentEndpointSecurityTest.JwtSecurityTestConfiguration.class, PublicTestConfiguration.class);
        context.refresh();
        service = context.getBean(PublicAuthenticationService.class);
        when(service.authenticate(any())).thenReturn(Optional.empty());
        when(service.authenticate("valid-public-session")).thenReturn(Optional.of(principal));
        when(service.current(principal)).thenReturn(new PublicUserDetails(principal.publicId(), "Person", "person@example.com"));
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @AfterEach
    void close() { context.close(); }

    private String managementToken() {
        var claims = JwtClaimsSet.builder().issuer("ethnowear-api").subject("admin")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60))
                .claim("roles", List.of("ADMINISTRATOR")).build();
        return context.getBean(JwtEncoder.class).encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    @Test
    void publicCookieCannotAuthorizeManagementAndManagementJwtCannotAuthorizePublic() throws Exception {
        mvc.perform(get("/api/admin/security-check").cookie(new Cookie(PublicAuthCookies.SESSION, "valid-public-session")))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/public/auth/me").header("Authorization", "Bearer " + managementToken()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("PUBLIC_AUTH_REQUIRED"));
        mvc.perform(get("/api/admin/security-check").header("Authorization", "Bearer " + managementToken()))
                .andExpect(status().isOk());
    }

    @Test
    void publicMeReturnsOnlySafeProfileAndNeverManagementRoles() throws Exception {
        mvc.perform(get("/api/public/auth/me").cookie(new Cookie(PublicAuthCookies.SESSION, "valid-public-session")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(principal.publicId().toString()))
                .andExpect(jsonPath("$.roles").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void csrfIsRequiredForLoginChallengeAndLogout() throws Exception {
        for (String path : List.of("/google", "/google/challenge", "/logout")) {
            mvc.perform(post("/api/public/auth" + path).contentType("application/json").content("{\"credential\":\"abc\"}"))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("PUBLIC_ACCESS_DENIED"));
        }
        verify(service, never()).login(any(), any(), any());
    }

    @Test
    void googleExchangeSetsIsolatedHttpOnlySessionCookieAndDoesNotReturnToken() throws Exception {
        var csrf = mvc.perform(get("/api/public/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        var csrfToken = new ObjectMapper().readTree(csrf.getContentAsString()).get("token").asText();
        when(service.login("google-credential", "nonce", null)).thenReturn(
                new PublicAuthenticationService.LoginResult("server-session", new PublicUserDetails(principal.publicId(), "Person", "person@example.com")));
        var response = mvc.perform(post("/api/public/auth/google")
                        .cookie(Objects.requireNonNull(csrf.getCookie("ETHNOWEAR_PUBLIC_CSRF")), new Cookie(PublicAuthCookies.NONCE, "nonce"))
                        .header("X-PUBLIC-CSRF", csrfToken).contentType("application/json")
                        .content("{\"credential\":\"google-credential\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.token").doesNotExist()).andReturn().getResponse();
        assertThat(response.getHeaders("Set-Cookie")).anySatisfy(value -> {
            assertThat(value).contains("ETHNOWEAR_PUBLIC_SESSION=server-session", "HttpOnly", "Secure", "SameSite=Lax", "Path=/api");
        });
        assertThat(response.getHeaders("Set-Cookie")).noneMatch(value -> value.startsWith("JSESSIONID="));
    }

    @Test
    void logoutIsIdempotentAndClearsOnlyPublicCookies() throws Exception {
        var csrf = mvc.perform(get("/api/public/auth/csrf")).andReturn().getResponse();
        var token = new ObjectMapper().readTree(csrf.getContentAsString()).get("token").asText();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/public/auth/logout").cookie(Objects.requireNonNull(csrf.getCookie("ETHNOWEAR_PUBLIC_CSRF")))
                            .header("X-PUBLIC-CSRF", token))
                    .andExpect(status().isNoContent()).andExpect(cookie().maxAge(PublicAuthCookies.SESSION, 0));
        }
        verify(service, times(2)).logout(null);
    }

    @Test
    void credentialCorsAllowsOnlyConfiguredOrigins() throws Exception {
        mvc.perform(options("/api/public/auth/google").header("Origin", "https://frontend.example")
                        .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "X-PUBLIC-CSRF,Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/public/auth/google").header("Origin", "https://attacker.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidInputHasSafeStableErrorAndNoCredentialEcho() throws Exception {
        var csrf = mvc.perform(get("/api/public/auth/csrf")).andReturn().getResponse();
        var token = new ObjectMapper().readTree(csrf.getContentAsString()).get("token").asText();
        mvc.perform(post("/api/public/auth/google").cookie(Objects.requireNonNull(csrf.getCookie("ETHNOWEAR_PUBLIC_CSRF")))
                        .header("X-PUBLIC-CSRF", token).contentType("application/json").content("{\"credential\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PUBLIC_AUTH_VALIDATION_FAILED"));
    }

    @Configuration
    static class PublicTestConfiguration {
        @Bean PublicAuthProperties publicAuthProperties() {
            return new PublicAuthProperties(true, "test.apps.googleusercontent.com", true, Duration.ofDays(7), List.of("https://frontend.example"));
        }
        @Bean PublicAuthenticationService publicAuthenticationService() { return mock(PublicAuthenticationService.class); }
        @Bean PublicAuthCookies publicAuthCookies(PublicAuthProperties properties) { return new PublicAuthCookies(properties); }
        @Bean PublicAuthController publicAuthController(PublicAuthenticationService service, PublicAuthProperties properties, PublicAuthCookies cookies) {
            return new PublicAuthController(service, properties, cookies);
        }
        @Bean PublicAuthExceptionHandler publicAuthExceptionHandler() { return new PublicAuthExceptionHandler(); }
    }
}
