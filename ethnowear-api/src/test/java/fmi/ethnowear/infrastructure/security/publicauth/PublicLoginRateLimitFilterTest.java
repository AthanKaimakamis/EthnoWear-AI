package fmi.ethnowear.infrastructure.security.publicauth;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.*;

class PublicLoginRateLimitFilterTest {
    @Test
    void limitsLoginRequestsWithoutTrustingForwardedAddresses() throws Exception {
        var filter = new PublicLoginRateLimitFilter();
        for (int i = 0; i < 31; i++) {
            var request = new MockHttpServletRequest("POST", "/api/public/auth/google/challenge");
            request.setServletPath("/api/public/auth/google/challenge");
            request.addHeader("X-Forwarded-For", "203.0.113." + i);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> { });
            assertThat(response.getStatus()).isEqualTo(i < 30 ? 200 : 429);
            if (i == 30) {
                assertThat(response.getHeader("Retry-After")).isEqualTo("60");
                assertThat(response.getContentAsString()).contains("PUBLIC_LOGIN_RATE_LIMITED");
            }
        }
    }
}
