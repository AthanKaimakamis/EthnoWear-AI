package fmi.ethnowear.infrastructure.security.publicauth;

import fmi.ethnowear.application.service.publicauth.PublicAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class PublicSessionAuthenticationFilter extends OncePerRequestFilter {
    private final PublicAuthenticationService authentication;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = PublicAuthCookies.read(request, PublicAuthCookies.SESSION);
        try {
            if (token != null) {
                authentication.authenticate(token).ifPresent(principal -> {
                    var context = SecurityContextHolder.createEmptyContext();
                    context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                            principal, null, List.of(new SimpleGrantedAuthority("PUBLIC_USER"))));
                    SecurityContextHolder.setContext(context);
                });
            }
        } catch (DataAccessException ex) {
            response.setStatus(503);
            response.setContentType("application/json");
            response.getWriter().write("{\"code\":\"PUBLIC_AUTH_UNAVAILABLE\",\"message\":\"Public sign-in is temporarily unavailable\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
