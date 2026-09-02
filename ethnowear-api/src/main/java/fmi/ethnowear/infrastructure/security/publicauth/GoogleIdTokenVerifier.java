package fmi.ethnowear.infrastructure.security.publicauth;

import fmi.ethnowear.application.exception.PublicAuthException;
import fmi.ethnowear.application.model.publicauth.GoogleIdentity;
import fmi.ethnowear.application.port.publicauth.GoogleIdentityVerifier;
import fmi.ethnowear.config.PublicAuthProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;

@Component
public class GoogleIdTokenVerifier implements GoogleIdentityVerifier {
    public static final String ISSUER = "https://accounts.google.com";
    private final PublicAuthProperties properties;
    private final JwtDecoder decoder;
    private final Clock clock;

    @Autowired
    public GoogleIdTokenVerifier(PublicAuthProperties properties, Clock clock) {
        this(properties, decoder(), clock);
    }

    GoogleIdTokenVerifier(PublicAuthProperties properties, JwtDecoder decoder, Clock clock) {
        this.properties = properties;
        this.decoder = decoder;
        this.clock = clock;
    }

    private static JwtDecoder decoder() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(5));
        // Fixed Google key endpoint; never follow a token-supplied issuer/jku URL.
        return NimbusJwtDecoder.withJwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                .restOperations(new RestTemplate(factory)).build();
    }

    @Override
    public GoogleIdentity verify(String credential, String expectedNonce) {
        if (!properties.enabled())
            throw new PublicAuthException(HttpStatus.SERVICE_UNAVAILABLE, "PUBLIC_LOGIN_DISABLED", "Public sign-in is not configured");
        try {
            Jwt jwt = decoder.decode(credential);
            String issuer = jwt.getClaimAsString("iss");
            String subject = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            String name = jwt.getClaimAsString("name");
            String authorizedParty = jwt.getClaimAsString("azp");
            if (issuer == null || !Set.of(ISSUER, "accounts.google.com").contains(issuer)
                    || jwt.getAudience() == null || !jwt.getAudience().contains(properties.googleClientId())
                    || (jwt.getAudience().size() > 1 && authorizedParty == null)
                    || (authorizedParty != null && !authorizedParty.equals(properties.googleClientId()))
                    || jwt.getExpiresAt() == null || !jwt.getExpiresAt().isAfter(clock.instant())
                    || jwt.getIssuedAt() == null || jwt.getIssuedAt().isAfter(clock.instant().plusSeconds(60))
                    || subject == null || subject.isBlank() || subject.length() > 255
                    || email == null || email.isBlank() || email.length() > 320
                    || !Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified"))
                    || expectedNonce == null || !expectedNonce.equals(jwt.getClaimAsString("nonce")))
                throw invalid();
            if (name == null || name.isBlank())
                name = "Google user";
            if (name.length() > 200)
                name = name.substring(0, 200);
            return new GoogleIdentity(ISSUER, subject, email, name);
        } catch (BadJwtException | IllegalArgumentException ex) {
            throw invalid();
        } catch (JwtException ex) {
            throw new PublicAuthException(HttpStatus.SERVICE_UNAVAILABLE, "GOOGLE_VERIFICATION_UNAVAILABLE", "Google sign-in is temporarily unavailable");
        }
    }

    private PublicAuthException invalid() {
        return new PublicAuthException(HttpStatus.UNAUTHORIZED, "GOOGLE_CREDENTIAL_INVALID", "Google sign-in could not be verified");
    }
}
