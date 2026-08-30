package fmi.ethnowear.infrastructure.security.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.config.WorkerApiProperties;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.unit.DataSize;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerApiAuthenticationFilterTest {

    private static final String TOKEN =
            "test-worker-token-with-at-least-32-bytes";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsMissingIncorrectAndBearerTokens() throws Exception {
        assertRejected(null);
        assertRejected("Worker incorrect-token-with-at-least-32-bytes");
        assertRejected("Bearer " + TOKEN);
    }

    @Test
    void authenticatesOnlyWithTheConfiguredWorkerToken() throws Exception {
        WorkerApiAuthenticationFilter filter = filter(true);
        MockHttpServletRequest request = workerRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Worker " + TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Authentication> authentication = new AtomicReference<>();
        FilterChain chain = (ignoredRequest, ignoredResponse) -> authentication.set(
                SecurityContextHolder.getContext().getAuthentication()
        );

        filter.doFilter(request, response, chain);

        assertThat(authentication.get()).isNotNull();
        assertThat(authentication.get().getAuthorities())
                .extracting("authority")
                .containsExactly(WorkerApiAuthenticationFilter.WORKER_AUTHORITY);
    }

    @Test
    void hidesTheWorkerApiWhenDisabled() throws Exception {
        WorkerApiAuthenticationFilter filter = filter(false);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(workerRequest(), response, (request, result) -> {
            throw new AssertionError("Disabled worker request reached the filter chain");
        });

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).get("code").asText())
                .isEqualTo("WORKER_API_DISABLED");
    }

    @Test
    void leavesPreflightRequestsToTheSecurityConfiguration() throws Exception {
        WorkerApiAuthenticationFilter filter = filter(true);
        MockHttpServletRequest request = workerRequest();
        request.setMethod("OPTIONS");
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);

        filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (ignoredRequest, ignoredResponse) -> invoked.set(true)
        );

        assertThat(invoked.get()).isTrue();
    }

    private void assertRejected(String authorization) throws Exception {
        WorkerApiAuthenticationFilter filter = filter(true);
        MockHttpServletRequest request = workerRequest();

        if(authorization != null)
            request.addHeader(HttpHeaders.AUTHORIZATION, authorization);

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
            throw new AssertionError("Invalid worker request reached the filter chain");
        });

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(objectMapper.readTree(response.getContentAsByteArray()).get("code").asText())
                .isEqualTo("INVALID_WORKER_TOKEN");
    }

    private MockHttpServletRequest workerRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/api/internal/worker/jobs/claim"
        );
        request.setServletPath("/api/internal/worker/jobs/claim");
        return request;
    }

    private WorkerApiAuthenticationFilter filter(boolean enabled) {
        return new WorkerApiAuthenticationFilter(
                properties(enabled),
                objectMapper
        );
    }

    private WorkerApiProperties properties(boolean enabled) {
        return new WorkerApiProperties(
                enabled,
                enabled ? TOKEN : null,
                Duration.ofSeconds(30),
                Duration.ofSeconds(120),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofMinutes(30),
                DataSize.ofMegabytes(250),
                DataSize.ofMegabytes(25),
                2000,
                300,
                20000,
                20000,
                100000000,
                2_000_000,
                DataSize.ofMegabytes(10)
        );
    }
}
