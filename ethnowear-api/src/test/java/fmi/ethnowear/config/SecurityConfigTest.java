package fmi.ethnowear.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityConfigTest {

    @Test
    void usesCostTwelveBcryptWithDelegatingPrefix() {
        SecurityConfig config = new SecurityConfig();
        PasswordEncoder encoder = config.passwordEncoder();
        String encoded = encoder.encode("ExamplePassword1!");

        assertTrue(encoded.startsWith("{bcrypt}$2"));
        assertTrue(encoder.matches("ExamplePassword1!", encoded));
    }

    @Test
    void rejectsJwtSecretsShorterThanHs256Requires() {
        SecurityConfig config = new SecurityConfig();
        JwtProperties properties = new JwtProperties(
                "too-short",
                "ethnowear-api",
                java.time.Duration.ofMinutes(15)
        );

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> config.jwtSecretKey(properties)
        );

        assertEquals(
                "ETHNOWEAR_JWT_SECRET must contain at least 32 UTF-8 bytes",
                exception.getMessage()
        );
    }
}
