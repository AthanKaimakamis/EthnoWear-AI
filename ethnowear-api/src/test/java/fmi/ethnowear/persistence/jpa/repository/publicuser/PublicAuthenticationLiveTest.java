package fmi.ethnowear.persistence.jpa.repository.publicuser;

import fmi.ethnowear.application.exception.PublicAuthException;
import fmi.ethnowear.application.model.publicauth.GoogleIdentity;
import fmi.ethnowear.application.port.publicauth.GoogleIdentityVerifier;
import fmi.ethnowear.application.service.publicauth.PublicAuthenticationService;
import fmi.ethnowear.config.PublicAuthProperties;
import fmi.ethnowear.persistence.jpa.entity.publicuser.*;
import fmi.ethnowear.util.ContentHashUtils;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@EnabledIfEnvironmentVariable(named = "ETHNOWEAR_LIVE_DB_TESTS", matches = "true")
@Import({PublicAuthenticationService.class, PublicAuthenticationLiveTest.Config.class})
class PublicAuthenticationLiveTest {
    @Autowired PublicAuthenticationService service;
    @Autowired PublicUserRepository users;
    @Autowired PublicUserSessionRepository sessions;
    @Autowired PublicUserIdentityRepository identities;
    @Autowired EntityManager em;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean GoogleIdentityVerifier google;

    private String subject() { return "test-" + UUID.randomUUID(); }
    private void google(String subject) {
        when(google.verify(anyString(), anyString())).thenReturn(
                new GoogleIdentity("https://accounts.google.com", subject, "test@example.invalid", "Test"));
    }

    @Test
    void signInReusesPublicAccountAcrossSessionsAndRevokesLogoutWithoutTouchingManagement() {
        int managementCount = jdbc.queryForObject("SELECT COUNT(*) FROM ethnowear.Users", Integer.class);
        google(subject());
        String nonce = service.challenge(null).nonce();
        var first = service.login("credential", nonce, null);
        assertThat(service.authenticate(first.token())).isPresent();
        assertThatThrownBy(() -> service.login("credential", nonce, null)).isInstanceOf(PublicAuthException.class);
        // A rejected nested transaction marks the outer test transaction rollback-only;
        // the remaining reads/writes are still verified and rolled back at test end.
        var second = service.login("credential", service.challenge(null).nonce(), null);
        assertThat(second.user().userId()).isEqualTo(first.user().userId());
        assertThat(second.token()).isNotEqualTo(first.token());
        service.logout(first.token());
        em.clear();
        assertThat(service.authenticate(first.token())).isEmpty();
        assertThat(service.authenticate(second.token())).isPresent();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ethnowear.Users", Integer.class)).isEqualTo(managementCount);
    }

    @Test
    void mapsDatabaseVersionAndRejectsDisabledPublicSession() {
        google(subject());
        var result = service.login("credential", service.challenge(null).nonce(), null);
        var principal = service.authenticate(result.token()).orElseThrow();
        var user = users.findById(principal.userId()).orElseThrow();
        byte[] version = user.getRowVersion();
        assertThat(version).hasSize(8);
        user.recordLogin("Updated", user.getEmail(), LocalDateTime.now(ZoneOffset.UTC));
        users.flush();
        assertThat(user.getRowVersion()).isNotEqualTo(version);
        jdbc.update("UPDATE ethnowear.PublicUsers SET Enabled = 0 WHERE Id = ?", user.getId());
        em.clear();
        assertThat(service.authenticate(result.token())).isEmpty();
    }

    @Test
    void sqlEnforcesExternalIdentityUniqueness() {
        var user = users.saveAndFlush(new PublicUser("Test", "test@example.invalid", LocalDateTime.now(ZoneOffset.UTC)));
        String subject = subject();
        identities.saveAndFlush(new PublicUserIdentity(user, "https://accounts.google.com", subject));
        assertThatThrownBy(() -> identities.saveAndFlush(new PublicUserIdentity(user, "https://accounts.google.com", subject)))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test
    void expiryAndRevocationAreAuthoritativeInSql() {
        var user = users.saveAndFlush(new PublicUser("Test", "test@example.invalid", LocalDateTime.now(ZoneOffset.UTC)));
        var expiry = LocalDateTime.now(ZoneOffset.UTC).plusHours(1);
        String token = "s".repeat(43);
        String hash = ContentHashUtils.sha256(token);
        sessions.saveAndFlush(new PublicUserSession(user, hash, expiry));
        assertThat(sessions.findByTokenHashAndRevokedAtIsNullAndExpiresAtAfter(hash, expiry)).isEmpty();
        sessions.revoke(hash, LocalDateTime.now(ZoneOffset.UTC));
        em.clear();
        assertThat(service.authenticate(token)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentFirstLoginsCreateOnePublicAccountAndNonceReplayCreatesOneSession() throws Exception {
        String subject = subject();
        google(subject);
        var first = service.challenge(null);
        var second = service.challenge(null);
        var replay = service.challenge(null);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var gate = new CountDownLatch(1);
            var a = executor.submit(() -> { gate.await(); return service.login("credential", first.nonce(), null); });
            var b = executor.submit(() -> { gate.await(); return service.login("credential", second.nonce(), null); });
            gate.countDown();
            var left = a.get(20, TimeUnit.SECONDS);
            var right = b.get(20, TimeUnit.SECONDS);
            assertThat(left.user().userId()).isEqualTo(right.user().userId());

            Callable<Boolean> attempt = () -> {
                try { service.login("credential", replay.nonce(), null); return true; }
                catch (PublicAuthException ex) {
                    assertThat(ex.getCode()).isEqualTo("PUBLIC_LOGIN_CHALLENGE_INVALID");
                    return false;
                }
            };
            var results = executor.invokeAll(List.of(attempt, attempt));
            assertThat(List.of(results.get(0).get(), results.get(1).get())).containsExactlyInAnyOrder(true, false);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ethnowear.PublicUserIdentities WHERE Subject = ?", Integer.class, subject)).isEqualTo(1);
        } finally {
            var ids = jdbc.queryForList("SELECT PublicUserId FROM ethnowear.PublicUserIdentities WHERE Subject = ?", Long.class, subject);
            for (long id : ids) {
                jdbc.update("DELETE FROM ethnowear.PublicUserSessions WHERE PublicUserId = ?", id);
                jdbc.update("DELETE FROM ethnowear.PublicUserIdentities WHERE PublicUserId = ?", id);
                jdbc.update("DELETE FROM ethnowear.PublicUsers WHERE Id = ?", id);
            }
            for (String nonce : List.of(first.nonce(), second.nonce(), replay.nonce()))
                jdbc.update("DELETE FROM ethnowear.PublicLoginChallenges WHERE NonceHash = ?", ContentHashUtils.sha256(nonce));
        }
    }

    @TestConfiguration
    static class Config {
        @Bean PublicAuthProperties publicAuthProperties() { return new PublicAuthProperties(true, "test.apps.googleusercontent.com", true, null, null); }
        @Bean Clock clock() { return Clock.systemUTC(); }
        @Bean TransactionTemplate transactions(PlatformTransactionManager manager) { return new TransactionTemplate(manager); }
    }
}
