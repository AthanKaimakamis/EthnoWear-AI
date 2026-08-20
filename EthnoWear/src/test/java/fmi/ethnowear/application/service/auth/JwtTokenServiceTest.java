package fmi.ethnowear.application.service.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import fmi.ethnowear.application.dto.auth.AdminTokenDetails;
import fmi.ethnowear.config.JwtProperties;
import fmi.ethnowear.domain.model.user.RoleName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");
    private static final String SECRET = "test-jwt-secret-with-at-least-32-bytes";

    @Test
    void issuesDatabaseIdentityAndRoleClaims() {
        JwtTokenService service = service();
        EthnoWearUserPrincipal principal = principal(false);

        AdminTokenDetails details = service.issue(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );

        Jwt jwt = decoder().decode(details.accessToken());

        assertEquals("Bearer", details.tokenType());
        assertEquals(900, details.expiresIn());
        assertFalse(details.passwordChangeRequired());
        assertEquals(7L, ((Number) jwt.getClaim("userId")).longValue());
        assertEquals(4, ((Number) jwt.getClaim("tokenVersion")).intValue());
        assertEquals(List.of("ADMINISTRATOR", "EDITOR"), jwt.getClaimAsStringList("roles"));
        assertEquals(Boolean.FALSE, jwt.getClaim("mustChangePassword"));
    }

    @Test
    void withholdsRolesUntilTemporaryPasswordIsChanged() {
        JwtTokenService service = service();
        EthnoWearUserPrincipal principal = principal(true);

        AdminTokenDetails details = service.issue(
                UsernamePasswordAuthenticationToken.authenticated(
                        principal,
                        null,
                        principal.getAuthorities()
                )
        );

        Jwt jwt = decoder().decode(details.accessToken());

        assertTrue(details.passwordChangeRequired());
        assertTrue(jwt.getClaimAsStringList("roles").isEmpty());
        assertEquals(Boolean.TRUE, jwt.getClaim("mustChangePassword"));
    }

    private JwtTokenService service() {
        JwtProperties properties = new JwtProperties(
                SECRET,
                "ethnowear-api",
                Duration.ofMinutes(15)
        );

        return new JwtTokenService(
                new NimbusJwtEncoder(new ImmutableSecret<>(secretKey())),
                properties,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    private NimbusJwtDecoder decoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder;
    }

    private SecretKey secretKey() {
        return new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
    }

    private EthnoWearUserPrincipal principal(boolean mustChangePassword) {
        return EthnoWearUserPrincipal.of(
                7L,
                "admin",
                "{bcrypt}hash",
                true,
                null,
                mustChangePassword,
                mustChangePassword
                        ? LocalDateTime.ofInstant(NOW.plusSeconds(3600), ZoneOffset.UTC)
                        : null,
                4,
                Set.of(RoleName.ADMINISTRATOR, RoleName.EDITOR),
                LocalDateTime.ofInstant(NOW, ZoneOffset.UTC)
        );
    }
}
