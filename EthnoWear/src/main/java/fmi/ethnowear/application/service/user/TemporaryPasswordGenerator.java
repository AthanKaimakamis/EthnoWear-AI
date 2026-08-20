package fmi.ethnowear.application.service.user;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
public class TemporaryPasswordGenerator {

    private static final int PASSWORD_LENGTH = 20;

    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*+-=?";
    private static final String ALL = UPPERCASE + LOWERCASE + DIGITS + SYMBOLS;

    private final SecureRandom secureRandom = new SecureRandom();

    public GeneratedTemporaryPassword generate() {
        char[] password = new char[PASSWORD_LENGTH];

        password[0] = randomCharacter(UPPERCASE);
        password[1] = randomCharacter(LOWERCASE);
        password[2] = randomCharacter(DIGITS);
        password[3] = randomCharacter(SYMBOLS);

        for (int i = 4; i < password.length; i++)
            password[i] = randomCharacter(ALL);

        shuffle(password);

        return new GeneratedTemporaryPassword(new String(password));
    }

    private char randomCharacter(@NonNull String characters) {
        return characters.charAt(secureRandom.nextInt(characters.length()));
    }

    private void shuffle(char @NonNull [] values) {
        for (int i = values.length - 1; i > 0; i--) {
            int target = secureRandom.nextInt(i + 1);
            char value = values[i];
            values[i] = values[target];
            values[target] = value;
        }
    }
}