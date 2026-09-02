package fmi.ethnowear.config;

import fmi.ethnowear.application.service.publicauth.PublicAuthenticationService;
import fmi.ethnowear.infrastructure.security.publicauth.PublicLoginRateLimitFilter;
import fmi.ethnowear.infrastructure.security.publicauth.PublicSessionAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class PublicSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain publicSecurityFilterChain(
            HttpSecurity http, PublicAuthProperties properties, PublicAuthenticationService authentication
    ) throws Exception {
        var csrf = new CookieCsrfTokenRepository();
        csrf.setCookieName("ETHNOWEAR_PUBLIC_CSRF");
        csrf.setHeaderName("X-PUBLIC-CSRF");
        csrf.setCookiePath("/api");
        csrf.setCookieCustomizer(cookie -> cookie.httpOnly(true).secure(properties.secureCookies()).sameSite("Lax"));
        var cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.allowedOrigins());
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Content-Type", "X-PUBLIC-CSRF", "Last-Event-ID"));
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);

        // Neither management JWTs nor worker credentials are interpreted by this chain.
        return http.securityMatcher("/api/public/**", "/api/conversations/**")
                .cors(c -> c.configurationSource(source))
                .csrf(c -> c.csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .addFilterBefore(new PublicLoginRateLimitFilter(), CsrfFilter.class)
                .addFilterBefore(new PublicSessionAuthenticationFilter(authentication), AnonymousAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/public/auth/me").hasAuthority("PUBLIC_USER")
                        .requestMatchers("/api/public/auth/**", "/api/conversations/**").permitAll()
                        .anyRequest().denyAll())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint((request, response, ex) -> {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"code\":\"PUBLIC_AUTH_REQUIRED\",\"message\":\"Public sign-in is required\"}");
                        })
                        .accessDeniedHandler((request, response, ex) -> {
                            response.setStatus(403);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"code\":\"PUBLIC_ACCESS_DENIED\",\"message\":\"Access denied or invalid CSRF token\"}");
                        }))
                .build();
    }
}
