package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.application.exception.PasswordPolicyException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordPolicyTest {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsPasswordMeetingTheCompletePolicy() {
        assertDoesNotThrow(() -> policy.validate("StrongPassword1!", "editor"));
    }

    @Test
    void rejectsWeakPasswordsAndUsernameReuse() {
        assertThrows(PasswordPolicyException.class, () -> policy.validate("Short1!", "editor"));
        assertThrows(PasswordPolicyException.class, () -> policy.validate("alllowercase1!", "editor"));
        assertThrows(PasswordPolicyException.class, () -> policy.validate("ALLUPPERCASE1!", "editor"));
        assertThrows(PasswordPolicyException.class, () -> policy.validate("NoDigitsHere!", "editor"));
        assertThrows(PasswordPolicyException.class, () -> policy.validate("NoSymbolsHere1", "editor"));
        assertThrows(PasswordPolicyException.class, () -> policy.validate("Editor", "editor"));
    }
}
