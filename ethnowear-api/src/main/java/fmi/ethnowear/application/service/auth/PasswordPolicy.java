package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.application.exception.PasswordPolicyException;
import org.springframework.stereotype.Component;

@Component
public class PasswordPolicy {

    private static final int MINIMUM_LENGTH = 12;
    private static final int MAXIMUM_LENGTH = 128;

    public void validate(String password, String username) {
        if (password == null || password.length() < MINIMUM_LENGTH || password.length() > MAXIMUM_LENGTH)
            throw new PasswordPolicyException("Password must contain between 12 and 128 characters");

        if (password.equalsIgnoreCase(username))
            throw new PasswordPolicyException("Password cannot match the username");

        if (password.chars().noneMatch(Character::isUpperCase)
                || password.chars().noneMatch(Character::isLowerCase)
                || password.chars().noneMatch(Character::isDigit)
                || password.chars().allMatch(Character::isLetterOrDigit))
            throw new PasswordPolicyException("Password must contain uppercase, lowercase, number, and symbol characters");
    }
}
