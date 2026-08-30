package fmi.ethnowear.infrastructure.security.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.WorkerApiError;
import fmi.ethnowear.config.WorkerApiProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Component
@RequiredArgsConstructor
public class WorkerApiAuthenticationFilter extends OncePerRequestFilter {

    public static final String WORKER_AUTHORITY = "INTERNAL_WORKER";

    private static final String PATH = "/api/internal/worker";
    private static final String AUTHENTICATION_PREFIX = "Worker ";
    private static final int MAXIMUM_HEADER_LENGTH = 1024;

    private final WorkerApiProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();

        if(!contextPath.isEmpty())
            path = path.substring(contextPath.length());

        return HttpMethod.OPTIONS.matches(request.getMethod())
                || !path.equals(PATH) && !path.startsWith(PATH + "/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        if (!properties.enabled()) {
            error(
                    response,
                    HttpStatus.NOT_FOUND,
                    "WORKER_API_DISABLED",
                    "The internal worker API is not enabled"
            );
            return;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if(!isValid(header)) {
            error(
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_WORKER_TOKEN",
                    "Worker authentication is required"
            );
            return;
        }

        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                "internal-worker",
                null,
                List.of(new SimpleGrantedAuthority(WORKER_AUTHORITY))
        );

        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        filterChain.doFilter(request, response);
    }

    private boolean isValid(String header) {
        if(header == null
                || header.length() > MAXIMUM_HEADER_LENGTH
                || !header.startsWith(AUTHENTICATION_PREFIX))
            return false;

        String suppliedToken = header.substring(AUTHENTICATION_PREFIX.length());

        if(suppliedToken.isBlank())
            return false;

        return MessageDigest.isEqual(
                properties.token().getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8)
        );
    }

    private void error(
            @NonNull HttpServletResponse response,
            @NonNull HttpStatus status,
            String code,
            String message
    ) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        objectMapper.writeValue(
                response.getOutputStream(),
                new WorkerApiError(
                        status.value(),
                        status.getReasonPhrase(),
                        code,
                        message
                )
        );
    }
}
