package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.application.dto.auth.PasswordChangeCommand;
import fmi.ethnowear.application.exception.InvalidCurrentPasswordException;
import fmi.ethnowear.persistence.jpa.entity.user.User;
import fmi.ethnowear.persistence.jpa.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Proxy;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class PasswordChangeServiceTest {

    @Test
    void replacesTemporaryPasswordAndRevokesExistingTokens() {
        PasswordEncoder encoder = encoder();
        User user = new User("editor", null);
        user.assignPassword(
                encoder.encode("TemporaryPass1!"),
                true,
                LocalDateTime.now().plusHours(1)
        );
        user.enable();
        int previousTokenVersion = user.getTokenVersion();
        AtomicReference<User> saved = new AtomicReference<>();
        PasswordChangeService service = new PasswordChangeService(
                repository(user, saved),
                encoder,
                new PasswordPolicy()
        );

        service.changePassword(
                1L,
                new PasswordChangeCommand(
                        "TemporaryPass1!",
                        "PermanentPass2!"
                )
        );

        assertSame(user, saved.get());
        assertTrue(encoder.matches("PermanentPass2!", user.getPasswordHash()));
        assertFalse(user.isMustChangePassword());
        assertNull(user.getTemporaryPasswordExpiresAt());
        assertTrue(user.getTokenVersion() > previousTokenVersion);
    }

    @Test
    void rejectsAnIncorrectCurrentPassword() {
        PasswordEncoder encoder = encoder();
        User user = new User("editor", null);
        user.assignPassword(encoder.encode("TemporaryPass1!"), true, LocalDateTime.now().plusHours(1));
        PasswordChangeService service = new PasswordChangeService(
                repository(user, new AtomicReference<>()),
                encoder,
                new PasswordPolicy()
        );

        assertThrows(
                InvalidCurrentPasswordException.class,
                () -> service.changePassword(
                        1L,
                        new PasswordChangeCommand("WrongPassword1!", "PermanentPass2!")
                )
        );
    }

    private PasswordEncoder encoder() {
        return new DelegatingPasswordEncoder(
                "bcrypt",
                Map.of("bcrypt", new BCryptPasswordEncoder(4))
        );
    }

    private UserRepository repository(User user, AtomicReference<User> saved) {
        return (UserRepository) Proxy.newProxyInstance(
                UserRepository.class.getClassLoader(),
                new Class<?>[]{UserRepository.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "findById" -> Optional.of(user);
                    case "saveAndFlush" -> {
                        saved.set((User) arguments[0]);
                        yield arguments[0];
                    }
                    case "toString" -> "UserRepositoryTestDouble";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
