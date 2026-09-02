package fmi.ethnowear.infrastructure.security.publicauth;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

public class PublicLoginRateLimitFilter extends OncePerRequestFilter {
    private final Cache<String, AtomicInteger> requests = Caffeine.newBuilder()
            .maximumSize(10000).expireAfterWrite(Duration.ofMinutes(1)).build();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !request.getServletPath().startsWith("/api/public/auth/google");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // Do not trust arbitrary X-Forwarded-For values as rate-limit identities.
        if (requests.get(request.getRemoteAddr(), ignored -> new AtomicInteger()).incrementAndGet() > 30) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.setHeader("Retry-After", "60");
            response.getWriter().write("{\"code\":\"PUBLIC_LOGIN_RATE_LIMITED\",\"message\":\"Please retry shortly\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
