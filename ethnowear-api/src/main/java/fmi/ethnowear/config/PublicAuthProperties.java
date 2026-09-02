package fmi.ethnowear.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "ethnowear.security.public-auth")
public record PublicAuthProperties(
        boolean enabled,
        String googleClientId,
        boolean secureCookies,
        Duration sessionTtl,
        List<String> allowedOrigins
) {
    public PublicAuthProperties {
        googleClientId = googleClientId == null ? "" : googleClientId.trim();
        sessionTtl = sessionTtl == null ? Duration.ofDays(7) : sessionTtl;
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
        if (sessionTtl.compareTo(Duration.ofMinutes(5)) < 0 || sessionTtl.compareTo(Duration.ofDays(30)) > 0)
            throw new IllegalArgumentException("Public session TTL must be between 5 minutes and 30 days");
        if (enabled && !googleClientId.matches("[A-Za-z0-9._-]+\\.apps\\.googleusercontent\\.com"))
            throw new IllegalArgumentException("A Google web client ID is required for public sign-in");
        for (String origin : allowedOrigins) {
            java.net.URI uri = java.net.URI.create(origin);
            boolean local = uri.getHost() != null && List.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !uri.getPath().isEmpty()
                    || !("https".equals(uri.getScheme()) || local && "http".equals(uri.getScheme())))
                throw new IllegalArgumentException("Public auth origins must be exact HTTPS origins or localhost");
            if (!secureCookies && !local)
                throw new IllegalArgumentException("Public sign-in outside localhost requires secure cookies");
        }
    }
}
