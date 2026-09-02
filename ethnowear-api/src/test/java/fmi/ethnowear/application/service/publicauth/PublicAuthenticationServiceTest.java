package fmi.ethnowear.application.service.publicauth;

import fmi.ethnowear.application.exception.PublicAuthException;
import fmi.ethnowear.application.model.publicauth.GoogleIdentity;
import fmi.ethnowear.application.port.publicauth.GoogleIdentityVerifier;
import fmi.ethnowear.config.PublicAuthProperties;
import fmi.ethnowear.persistence.jpa.entity.publicuser.*;
import fmi.ethnowear.persistence.jpa.repository.publicuser.*;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PublicAuthenticationServiceTest {
    private final PublicUserRepository users = mock(PublicUserRepository.class);
    private final PublicUserIdentityRepository identities = mock(PublicUserIdentityRepository.class);
    private final PublicUserSessionRepository sessions = mock(PublicUserSessionRepository.class);
    private final PublicLoginChallengeRepository challenges = mock(PublicLoginChallengeRepository.class);
    private final GoogleIdentityVerifier google = mock(GoogleIdentityVerifier.class);
    private final TransactionTemplate transactions = mock(TransactionTemplate.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final Instant instant = Instant.parse("2026-08-31T12:00:00Z");
    private final LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    private final String nonce = "n".repeat(43);
    private final GoogleIdentity identity = new GoogleIdentity("https://accounts.google.com", "subject", "person@example.com", "Person");
    private PublicAuthenticationService service;
    private PublicLoginChallenge challenge;

    @BeforeEach
    void setup() {
        service = new PublicAuthenticationService(new PublicAuthProperties(true, "test.apps.googleusercontent.com", true, null, null),
                google, users, identities, sessions, challenges, transactions, jdbc, Clock.fixed(instant, ZoneOffset.UTC));
        when(transactions.execute(any())).thenAnswer(invocation -> ((TransactionCallback<?>) invocation.getArgument(0)).doInTransaction(null));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString())).thenReturn(0);
        when(google.verify("credential", nonce)).thenReturn(identity);
        challenge = new PublicLoginChallenge(ContentHashUtils.sha256(nonce), now.plusMinutes(5));
        when(challenges.findByNonceHash(ContentHashUtils.sha256(nonce))).thenReturn(Optional.of(challenge));
        when(users.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void newAccountUsesVerifiedIdentityNotEmailAndStoresOnlyTokenHashes() {
        var result = service.login("credential", nonce, null);
        assertThat(result.token()).hasSize(43);
        assertThat(result.user().email()).isEqualTo(identity.email());
        verify(identities).findByIssuerAndSubject(identity.issuer(), identity.subject());
        verify(identities).save(argThat(i -> i.getSubject().equals(identity.subject())));
        verify(sessions).saveAndFlush(argThat(s -> s.getTokenHash().equals(ContentHashUtils.sha256(result.token()))));
        assertThat(challenge.getConsumedAt()).isEqualTo(now);
        assertThat(result.toString()).doesNotContain(result.token());
    }

    @Test
    void existingAccountKeepsIdentityAndRotatesOnlyCurrentSession() {
        var user = new PublicUser("Old name", "old@example.com", now.minusDays(1));
        when(identities.findByIssuerAndSubject(identity.issuer(), identity.subject()))
                .thenReturn(Optional.of(new PublicUserIdentity(user, identity.issuer(), identity.subject())));
        var result = service.login("credential", nonce, "s".repeat(43));
        verify(users, never()).saveAndFlush(any());
        verify(sessions).revoke(ContentHashUtils.sha256("s".repeat(43)), now);
        assertThat(result.user().userId()).isEqualTo(user.getPublicId());
        assertThat(user.getDisplayName()).isEqualTo("Person");
    }

    @Test
    void replayAndExpiredChallengesDoNotCreateSessions() {
        challenge.consume(now);
        assertThatThrownBy(() -> service.login("credential", nonce, null)).extracting("code").isEqualTo("PUBLIC_LOGIN_CHALLENGE_INVALID");
        when(challenges.findByNonceHash(anyString())).thenReturn(Optional.of(new PublicLoginChallenge("hash", now)));
        assertThatThrownBy(() -> service.login("credential", nonce, null)).isInstanceOf(PublicAuthException.class);
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void disabledAccountsCannotSignIn() {
        var user = new PublicUser("Person", identity.email(), now);
        ReflectionTestUtils.setField(user, "enabled", false);
        when(identities.findByIssuerAndSubject(anyString(), anyString())).thenReturn(Optional.of(new PublicUserIdentity(user, identity.issuer(), identity.subject())));
        assertThatThrownBy(() -> service.login("credential", nonce, null)).extracting("code").isEqualTo("PUBLIC_ACCOUNT_DISABLED");
        verify(sessions, never()).saveAndFlush(any());
    }

    @Test
    void missingNonceRejectsBeforeGoogleCall() {
        assertThatThrownBy(() -> service.login("credential", null, null)).extracting("code").isEqualTo("PUBLIC_LOGIN_CHALLENGE_INVALID");
        verifyNoInteractions(google);
    }

    @Test
    void malformedSessionsAreNotQueriedAndDisabledUsersAreRejected() {
        assertThat(service.authenticate("bad")).isEmpty();
        verifyNoInteractions(sessions);
        var user = new PublicUser("Person", identity.email(), now);
        ReflectionTestUtils.setField(user, "enabled", false);
        when(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(anyString(), any()))
                .thenReturn(Optional.of(new PublicUserSession(user, "hash", now.plusDays(1))));
        assertThat(service.authenticate("s".repeat(43))).isEmpty();
    }

    @Test
    void challengeReturnsRandomBoundedNonceButPersistsHashOnly() {
        var result = service.challenge(null);
        assertThat(result.nonce()).hasSize(43);
        assertThat(result.expiresAt()).isEqualTo(instant.plusSeconds(300));
        verify(challenges).saveAndFlush(argThat(c -> c.getNonceHash().equals(ContentHashUtils.sha256(result.nonce()))));
    }

    @Test
    void logoutRevokesOnlyPresentedSessionAndToleratesMissingCookie() {
        service.logout(null);
        verifyNoInteractions(sessions);
        service.logout("s".repeat(43));
        verify(sessions).revoke(ContentHashUtils.sha256("s".repeat(43)), now);
    }
}
