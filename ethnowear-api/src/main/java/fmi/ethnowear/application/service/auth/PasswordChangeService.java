package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.application.dto.auth.PasswordChangeCommand;
import fmi.ethnowear.application.exception.*;
import fmi.ethnowear.persistence.jpa.entity.user.User;
import fmi.ethnowear.persistence.jpa.repository.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
public class PasswordChangeService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    @Transactional
    public void changePassword(Long userId, PasswordChangeCommand command) {
        requireId(userId, "User");

        if (command == null)
            throw new IllegalArgumentException("Password change command is required");

        User user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        String currentHash = user.getPasswordHash();

        if (currentHash == null || !passwordEncoder.matches(command.currentPassword(), currentHash))
            throw new InvalidCurrentPasswordException();

        passwordPolicy.validate(
                command.newPassword(),
                user.getUsername()
        );

        if (passwordEncoder.matches(
                command.newPassword(),
                currentHash
        ))
            throw new PasswordPolicyException("New password must differ from the current password");

        user.completePasswordChange(passwordEncoder.encode(command.newPassword()));

        userRepository.saveAndFlush(user);
    }
}
