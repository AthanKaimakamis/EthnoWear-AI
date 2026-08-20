package fmi.ethnowear.tools.password;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordGeneratorTest {

    @Test
    void generatesStrongPasswordAndMatchingDelegatedBcryptHash() {
        GeneratedPassword generated = new PasswordGenerator().generate();

        assertEquals(PasswordGenerator.DEFAULT_LENGTH, generated.password().length());
        assertTrue(generated.password().chars().anyMatch(Character::isUpperCase));
        assertTrue(generated.password().chars().anyMatch(Character::isLowerCase));
        assertTrue(generated.password().chars().anyMatch(Character::isDigit));
        assertTrue(generated.password().chars().anyMatch(value -> !Character.isLetterOrDigit(value)));
        assertTrue(generated.passwordHash().startsWith("{bcrypt}$2a$12$"));
        assertTrue(new BCryptPasswordEncoder().matches(
                generated.password(),
                generated.passwordHash().substring("{bcrypt}".length())
        ));
    }
}
