package fmi.ethnowear.infrastructure.security.publicauth;

import fmi.ethnowear.application.exception.PublicAuthException;
import fmi.ethnowear.config.PublicAuthProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.oauth2.jwt.*;

import java.time.*;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;

class GoogleIdTokenVerifierTest {
    private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");
    private static final String CLIENT = "test.apps.googleusercontent.com";
    private final PublicAuthProperties properties = new PublicAuthProperties(true, CLIENT, true, Duration.ofDays(7), List.of());

    private GoogleIdTokenVerifier verifier(Consumer<Jwt.Builder> change) {
        var jwt = Jwt.withTokenValue("credential").header("alg", "RS256")
                .issuer(GoogleIdTokenVerifier.ISSUER).subject("google-subject")
                .audience(List.of(CLIENT)).issuedAt(NOW.minusSeconds(20)).expiresAt(NOW.plusSeconds(300))
                .claim("nonce", "nonce").claim("email", "person@example.com").claim("email_verified", true)
                .claim("name", "Person");
        change.accept(jwt);
        return new GoogleIdTokenVerifier(properties, ignored -> jwt.build(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void acceptsVerifiedGoogleIdentityAndNormalizesIssuer() {
        var identity = verifier(b -> b.claim("iss", "accounts.google.com")).verify("credential", "nonce");
        assertThat(identity.issuer()).isEqualTo(GoogleIdTokenVerifier.ISSUER);
        assertThat(identity.subject()).isEqualTo("google-subject");
        assertThat(identity.email()).isEqualTo("person@example.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"issuer", "audience", "expired", "noExpiry", "futureIssued", "nonce", "unverified", "subject", "email", "azp", "multiAudience"})
    void rejectsInvalidClaims(String scenario) {
        var verifier = verifier(b -> {
            switch (scenario) {
                case "issuer" -> b.claim("iss", "https://attacker.invalid");
                case "audience" -> b.audience(List.of("another-client"));
                case "expired" -> b.expiresAt(NOW.minusSeconds(1));
                case "noExpiry" -> b.claims(c -> c.remove("exp"));
                case "futureIssued" -> b.issuedAt(NOW.plusSeconds(100));
                case "nonce" -> b.claim("nonce", "another-nonce");
                case "unverified" -> b.claim("email_verified", false);
                case "subject" -> b.subject(" ");
                case "email" -> b.claim("email", "x".repeat(321));
                case "azp" -> b.claim("azp", "attacker-client");
                case "multiAudience" -> b.audience(List.of(CLIENT, "other"));
            }
        });
        assertThatThrownBy(() -> verifier.verify("credential", "nonce"))
                .isInstanceOf(PublicAuthException.class).extracting("code").isEqualTo("GOOGLE_CREDENTIAL_INVALID");
    }

    @Test
    void sanitizesDecoderFailuresAndDoesNotAcceptUnsignedTokens() {
        var verifier = new GoogleIdTokenVerifier(properties, ignored -> { throw new BadJwtException("secret raw input"); }, Clock.systemUTC());
        assertThatThrownBy(() -> verifier.verify("bad-token", "nonce"))
                .isInstanceOf(PublicAuthException.class).hasMessage("Google sign-in could not be verified");
    }

    @Test
    void disabledSignInDoesNotCallGoogle() {
        var disabled = new PublicAuthProperties(false, "", true, null, null);
        var verifier = new GoogleIdTokenVerifier(disabled, ignored -> { throw new AssertionError(); }, Clock.systemUTC());
        assertThatThrownBy(() -> verifier.verify("credential", "nonce"))
                .extracting("code").isEqualTo("PUBLIC_LOGIN_DISABLED");
    }

    @Test
    void verifiesRealRs256SignatureAndRejectsDifferentSigningKey() throws Exception {
        var key = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("test").generate();
        var wrongKey = new com.nimbusds.jose.jwk.gen.RSAKeyGenerator(2048).keyID("wrong").generate();
        var encoder = new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(
                new com.nimbusds.jose.jwk.JWKSet(key)));
        var realNow = Instant.now();
        var claims = JwtClaimsSet.builder().issuer(GoogleIdTokenVerifier.ISSUER).subject("subject")
                .audience(List.of(CLIENT)).issuedAt(realNow.minusSeconds(10)).expiresAt(realNow.plusSeconds(60))
                .claim("email", "person@example.com").claim("email_verified", true).claim("nonce", "nonce").build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256).keyId("test").build(), claims)).getTokenValue();
        var good = new GoogleIdTokenVerifier(properties, NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build(), Clock.systemUTC());
        assertThat(good.verify(token, "nonce").subject()).isEqualTo("subject");
        var bad = new GoogleIdTokenVerifier(properties, NimbusJwtDecoder.withPublicKey(wrongKey.toRSAPublicKey()).build(), Clock.systemUTC());
        assertThatThrownBy(() -> bad.verify(token, "nonce")).extracting("code").isEqualTo("GOOGLE_CREDENTIAL_INVALID");
    }

    @Test
    void keyServiceFailureIsTemporaryAndSanitized() {
        var verifier = new GoogleIdTokenVerifier(properties, ignored -> { throw new JwtException("internal transport detail"); }, Clock.systemUTC());
        assertThatThrownBy(() -> verifier.verify("credential", "nonce"))
                .hasMessage("Google sign-in is temporarily unavailable").extracting("code").isEqualTo("GOOGLE_VERIFICATION_UNAVAILABLE");
    }

    @Test
    void rejectsUnsafeConfiguration() {
        assertThatThrownBy(() -> new PublicAuthProperties(true, "", true, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicAuthProperties(true, CLIENT, false, null, List.of("https://example.com"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicAuthProperties(true, CLIENT, true, null, List.of("https://example.com/path"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PublicAuthProperties(true, CLIENT, true, Duration.ofDays(31), List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
