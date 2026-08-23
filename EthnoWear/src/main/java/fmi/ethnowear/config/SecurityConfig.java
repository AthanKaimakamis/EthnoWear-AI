package fmi.ethnowear.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import fmi.ethnowear.application.service.auth.JwtUserStateValidator;
import fmi.ethnowear.infrastructure.security.worker.WorkerApiAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;

import static fmi.ethnowear.infrastructure.security.worker.WorkerApiAuthenticationFilter.WORKER_AUTHORITY;

@EnableMethodSecurity
@Configuration
public class SecurityConfig {

    private static final int MINIMUM_HS256_SECRET_BYTES = 32;

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationConverter jwtAuthenticationConverter,
            WorkerApiAuthenticationFilter workerAuthenticationFilter
    ) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(
                                SessionCreationPolicy.STATELESS
                        ))
                .addFilterBefore(
                        workerAuthenticationFilter,
                        BearerTokenAuthenticationFilter.class
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                HttpMethod.OPTIONS,
                                "/**"
                        ).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/auth/login",
                                "/api/auth/admin/login"
                        ).permitAll()
                        .requestMatchers(
                                "/api/auth/me",
                                "/api/auth/refresh",
                                "/api/auth/password/change"
                        ).authenticated()
                        .requestMatchers("/api/admin/users/**")
                        .hasRole("ADMINISTRATOR")
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/admin/**"
                        ).hasAnyRole(
                                "ADMINISTRATOR",
                                "REVIEWER",
                                "EDITOR"
                        )
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/admin/archive-items/*/publish",
                                "/api/admin/archive-items/*/return-to-draft",
                                "/api/admin/archive-items/*/archive",
                                "/api/admin/document-pages/*/approve",
                                "/api/admin/document-pages/*/reject",
                                "/api/admin/document-pages/*/provenance-events/trust-change",
                                "/api/admin/document-pages/*/provenance-events/canonical-link",
                                "/api/admin/document-pages/*/provenance-events/canonical-merge",
                                "/api/admin/document-pages/*/provenance-events/canonical-link-reversal"
                        ).hasAnyRole(
                                "ADMINISTRATOR",
                                "REVIEWER"
                        )
                        .requestMatchers(
                                "/api/admin/ontology/**",
                                "/api/admin/sources/**",
                                "/api/admin/source-references/**",
                                "/api/admin/archive-items/**",
                                "/api/admin/archive-item-features/**",
                                "/api/admin/media-assets/**",
                                "/api/admin/media-entity-links/**",
                                "/api/admin/archive-item-media/**",
                                "/api/admin/media-feature-annotations/**",
                                "/api/admin/knowledge-chunks/**",
                                "/api/admin/documents/**",
                                "/api/admin/document-pages/**",
                                "/api/admin/document-processing-jobs/**"
                        ).hasAnyRole(
                                "ADMINISTRATOR",
                                "EDITOR"
                        )
                        .requestMatchers("/api/internal/worker/**")
                        .hasAuthority(WORKER_AUTHORITY)
                        .requestMatchers("/api/admin/**")
                        .denyAll()
                        .anyRequest()
                        .permitAll())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(
                                        jwtAuthenticationConverter
                                )))
                .build();
    }

    @Bean
    AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(
                "bcrypt",
                Map.of("bcrypt", new BCryptPasswordEncoder(12))
        );
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] secret = properties.secret().getBytes(StandardCharsets.UTF_8);

        if (secret.length < MINIMUM_HS256_SECRET_BYTES)
            throw new IllegalStateException("ETHNOWEAR_JWT_SECRET must contain at least 32 UTF-8 bytes");

        if (properties.ttl().isZero() || properties.ttl().isNegative())
            throw new IllegalStateException("ETHNOWEAR_JWT_TTL must be positive");

        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(
            SecretKey secretKey,
            JwtProperties properties,
            JwtUserStateValidator userStateValidator
    ) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(properties.issuer()),
                        userStateValidator
                )
        );

        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return authenticationConverter;
    }

    @Bean
    Clock jwtClock() {
        return Clock.systemUTC();
    }


}
