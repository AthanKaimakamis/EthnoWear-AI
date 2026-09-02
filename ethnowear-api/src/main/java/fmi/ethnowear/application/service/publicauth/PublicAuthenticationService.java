package fmi.ethnowear.application.service.publicauth;

import fmi.ethnowear.application.dto.publicauth.PublicLoginChallengeDetails;
import fmi.ethnowear.application.dto.publicauth.PublicUserDetails;
import fmi.ethnowear.application.exception.PublicAuthException;
import fmi.ethnowear.application.model.publicauth.GoogleIdentity;
import fmi.ethnowear.application.model.publicauth.PublicUserPrincipal;
import fmi.ethnowear.application.port.publicauth.GoogleIdentityVerifier;
import fmi.ethnowear.config.PublicAuthProperties;
import fmi.ethnowear.persistence.jpa.entity.publicuser.*;
import fmi.ethnowear.persistence.jpa.repository.publicuser.*;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PublicAuthenticationService {
    public static final Duration CHALLENGE_TTL = Duration.ofMinutes(5);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final PublicAuthProperties properties;
    private final GoogleIdentityVerifier google;
    private final PublicUserRepository users;
    private final PublicUserIdentityRepository identities;
    private final PublicUserSessionRepository sessions;
    private final PublicLoginChallengeRepository challenges;
    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public PublicLoginChallengeDetails challenge(String previousNonce) {
        requireEnabled();
        if (validToken(previousNonce))
            challenges.findByNonceHash(hash(previousNonce)).filter(c -> c.getConsumedAt() == null)
                    .ifPresent(c -> c.consume(now()));
        String nonce = randomToken();
        var expires = clock.instant().plus(CHALLENGE_TTL);
        challenges.saveAndFlush(new PublicLoginChallenge(hash(nonce), LocalDateTime.ofInstant(expires, ZoneOffset.UTC)));
        return new PublicLoginChallengeDetails(nonce, expires);
    }

    public LoginResult login(String credential, String nonce, String previousSession) {
        requireEnabled();
        if (!validToken(nonce))
            throw challengeInvalid();
        if (credential == null || credential.isBlank() || credential.length() > 8192)
            throw new PublicAuthException(HttpStatus.BAD_REQUEST, "PUBLIC_AUTH_VALIDATION_FAILED", "Invalid login input");
        // Google network verification happens before taking any database locks.
        GoogleIdentity identity = google.verify(credential, nonce);
        return transactions.execute(status -> loginVerified(identity, nonce, previousSession));
    }

    private LoginResult loginVerified(GoogleIdentity identity, String nonce, String previousSession) {
        var challenge = challenges.findByNonceHash(hash(nonce)).orElseThrow(this::challengeInvalid);
        var now = now();
        if (challenge.getConsumedAt() != null || !challenge.getExpiresAt().isAfter(now))
            throw challengeInvalid();

        // Serialize first sign-in across API instances; uniqueness is also enforced by SQL.
        Integer lockResult = jdbc.queryForObject("""
                DECLARE @result int;
                EXEC @result = sys.sp_getapplock @Resource = ?, @LockMode = 'Exclusive',
                    @LockOwner = 'Transaction', @LockTimeout = 5000;
                SELECT @result;
                """, Integer.class, "PublicGoogle:" + hash(identity.issuer() + ":" + identity.subject()));
        if (lockResult == null || lockResult < 0)
            throw new PublicAuthException(HttpStatus.SERVICE_UNAVAILABLE, "PUBLIC_LOGIN_BUSY", "Please retry sign-in");

        PublicUser user = identities.findByIssuerAndSubject(identity.issuer(), identity.subject())
                .map(PublicUserIdentity::getPublicUser)
                .orElseGet(() -> {
                    var created = users.saveAndFlush(new PublicUser(identity.displayName(), identity.email(), now));
                    identities.save(new PublicUserIdentity(created, identity.issuer(), identity.subject()));
                    return created;
                });
        if (!user.isEnabled())
            throw new PublicAuthException(HttpStatus.FORBIDDEN, "PUBLIC_ACCOUNT_DISABLED", "This public account is disabled");
        user.recordLogin(identity.displayName(), identity.email(), now);
        challenge.consume(now);
        if (validToken(previousSession))
            sessions.revoke(hash(previousSession), now);
        String token = randomToken();
        sessions.saveAndFlush(new PublicUserSession(user, hash(token), now.plus(properties.sessionTtl())));
        return new LoginResult(token, details(user));
    }

    @Transactional(readOnly = true)
    public Optional<PublicUserPrincipal> authenticate(String token) {
        if (!properties.enabled() || !validToken(token))
            return Optional.empty();
        return sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash(token), now())
                .filter(s -> s.getPublicUser().isEnabled())
                .map(s -> new PublicUserPrincipal(s.getPublicUser().getId(), s.getPublicUser().getPublicId(), s.getId()));
    }

    @Transactional(readOnly = true)
    public PublicUserDetails current(PublicUserPrincipal principal) {
        return users.findById(principal.userId()).filter(PublicUser::isEnabled).map(this::details)
                .orElseThrow(() -> new PublicAuthException(HttpStatus.UNAUTHORIZED, "PUBLIC_AUTH_REQUIRED", "Public sign-in is required"));
    }

    @Transactional
    public void logout(String token) {
        if (validToken(token))
            sessions.revoke(hash(token), now());
    }

    private PublicUserDetails details(PublicUser user) {
        return new PublicUserDetails(user.getPublicId(), user.getDisplayName(), user.getEmail());
    }

    private void requireEnabled() {
        if (!properties.enabled())
            throw new PublicAuthException(HttpStatus.SERVICE_UNAVAILABLE, "PUBLIC_LOGIN_DISABLED", "Public sign-in is not configured");
    }

    private PublicAuthException challengeInvalid() {
        return new PublicAuthException(HttpStatus.UNAUTHORIZED, "PUBLIC_LOGIN_CHALLENGE_INVALID", "Start Google sign-in again");
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean validToken(String token) {
        return token != null && token.matches("[A-Za-z0-9_-]{43}");
    }

    private static String hash(String value) {
        return ContentHashUtils.sha256(value);
    }

    public record LoginResult(String token, PublicUserDetails user) {
        @Override
        public String toString() {
            return "LoginResult[token=REDACTED]";
        }
    }
}
