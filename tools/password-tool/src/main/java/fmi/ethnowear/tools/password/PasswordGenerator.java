package fmi.ethnowear.tools.password;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.security.SecureRandom;

public final class PasswordGenerator {

    static final int DEFAULT_LENGTH = 20;

    private static final String UPPERCASE = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWERCASE = "abcdefghijkmnopqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "!@#$%&*+-=?";
    private static final String ALL_CHARACTERS = UPPERCASE + LOWERCASE + DIGITS + SYMBOLS;

    private final SecureRandom secureRandom;
    private final BCryptPasswordEncoder passwordEncoder;

    public PasswordGenerator() {
        this(new SecureRandom(), new BCryptPasswordEncoder(12));
    }

    PasswordGenerator(
            SecureRandom secureRandom,
            BCryptPasswordEncoder passwordEncoder
    ) {
        this.secureRandom = secureRandom;
        this.passwordEncoder = passwordEncoder;
    }

    public GeneratedPassword generate() {
        char[] password = new char[DEFAULT_LENGTH];
        password[0] = randomCharacter(UPPERCASE);
        password[1] = randomCharacter(LOWERCASE);
        password[2] = randomCharacter(DIGITS);
        password[3] = randomCharacter(SYMBOLS);

        for(int i = 4; i < password.length; i++)
            password[i] = randomCharacter(ALL_CHARACTERS);

        shuffle(password);
        String plaintext = new String(password);

        return new GeneratedPassword(
                plaintext,
                "{bcrypt}" + passwordEncoder.encode(plaintext)
        );
    }

    private char randomCharacter(String characters) {
        return characters.charAt(secureRandom.nextInt(characters.length()));
    }

    private void shuffle(char[] characters) {
        for(int i = characters.length - 1; i > 0; i--) {
            int target = secureRandom.nextInt(i + 1);
            char value = characters[i];
            characters[i] = characters[target];
            characters[target] = value;
        }
    }
}
