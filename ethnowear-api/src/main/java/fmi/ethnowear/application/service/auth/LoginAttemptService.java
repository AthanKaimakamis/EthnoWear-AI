package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.persistence.jpa.entity.user.User;
import fmi.ethnowear.persistence.jpa.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_MINUTES = 15;

    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSuccess(Long userId) {
        userRepository.findByIdAndDeletedAtIsNull(userId)
                .ifPresent(user -> user.recordSuccessfulLogin(now()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String username) {
        if (username == null || username.isBlank())
            return;

        userRepository.findForAuthenticationUpdate(
                username.trim().toUpperCase(Locale.ROOT)
        ).ifPresent(user -> recordFailure(user, now()));
    }

    private void recordFailure(@NonNull User user, LocalDateTime now) {
        if (!user.isEnabled())
            return;

        if (user.getLockedUntil() != null) {
            if (user.getLockedUntil().isAfter(now))
                return;

            user.unlock();
        }

        int nextAttempt = user.getFailedLoginAttempts() + 1;
        LocalDateTime lockedUntil = nextAttempt >= MAX_FAILED_ATTEMPTS
                ? now.plusMinutes(LOCK_MINUTES)
                : null;

        user.recordFailedLogin(lockedUntil);
    }

    @Contract(" -> new")
    private @NonNull LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC
        );
    }
}
