package fmi.ethnowear.application.service.user;

import fmi.ethnowear.application.dto.user.*;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.domain.model.user.RoleName;
import fmi.ethnowear.persistence.jpa.entity.user.*;
import fmi.ethnowear.persistence.jpa.repository.user.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
public class UserManagementService {

    private static final Duration TEMPORARY_PASSWORD_TTL = Duration.ofHours(24);

    private final UserRepository userRepository;
    private final UserInfoRepository userInfoRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final TemporaryPasswordGenerator passwordGenerator;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper mapper;
    private final UserQueryService queryService;
    private final Clock clock;

    @Transactional
    public CreatedUserDetails create(@NonNull UserCreateCommand command, Long createdByUserId) {
        String username = command.username().trim();
        String normalizedUsername = username.toUpperCase(Locale.ROOT);

        if (userRepository.existsByNormalizedUsername(normalizedUsername))
            throw new IllegalArgumentException("Username already exists");

        validateEmail(command.profile().email(), null);

        User creator = requireUser(createdByUserId);
        Set<Role> roles = requireRoles(command.roles());

        GeneratedTemporaryPassword generated = passwordGenerator.generate();
        LocalDateTime expiresAt = now().plus(TEMPORARY_PASSWORD_TTL);

        User user = userRepository.saveAndFlush(new User(username, creator));

        UserInfo info = new UserInfo(user);
        mapper.apply(info, command.profile());
        userInfoRepository.save(info);

        List<UserRole> assignments = roles.stream()
                .map(role -> new UserRole(user, role, creator))
                .toList();
        userRoleRepository.saveAll(assignments);

        user.assignPassword(
                passwordEncoder.encode(generated.plaintext()),
                true,
                expiresAt
        );
        user.enable();
        userRepository.saveAndFlush(user);

        return new CreatedUserDetails(
                queryService.findById(user.getId()),
                new TemporaryPasswordDetails(
                        generated.plaintext(),
                        expiresAt
                )
        );
    }

    @Transactional
    public UserDetails updateProfile(Long userId, @NonNull UserProfileCommand command) {
        requireUser(userId);
        validateEmail(command.email(), userId);

        UserInfo info = userInfoRepository
                .findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User information", userId));

        mapper.apply(info, command);
        userInfoRepository.saveAndFlush(info);

        return queryService.findById(userId);
    }

    @Transactional
    public UserDetails assignRole(
            Long userId,
            RoleName roleName,
            Long assignedByUserId
    ) {
        User user = requireUser(userId);
        User assignedBy = requireUser(assignedByUserId);
        Role role = requireRole(roleName);

        if (!userRoleRepository.existsByUser_IdAndRole_Name(
                userId,
                roleName
        )) {
            userRoleRepository.save(
                    new UserRole(user, role, assignedBy)
            );

            user.revokeTokens();
            userRepository.save(user);
        }

        userRoleRepository.flush();

        return queryService.findById(userId);
    }

    @Transactional
    public UserDetails removeRole(Long userId, RoleName roleName) {
        User user = requireUser(userId);

        UserRole assignment = userRoleRepository
                .findByUserIdAndRoleName(userId, roleName)
                .orElseThrow(() -> new IllegalArgumentException("User does not have role " + roleName));

        if (user.isEnabled() && userRoleRepository.countByUser_Id(userId) <= 1)
            throw new IllegalStateException("An enabled user must retain at least one role");

        if (user.isEnabled() && roleName == RoleName.ADMINISTRATOR
                && userRepository.lockEnabledAdministrators().size() <= 1)
            throw new IllegalStateException("The final enabled administrator cannot lose the administrator role");

        userRoleRepository.delete(assignment);
        user.revokeTokens();
        userRepository.saveAndFlush(user);

        return queryService.findById(userId);
    }

    @Transactional
    public UserDetails enable(Long userId) {
        User user = requireUser(userId);

        if (user.getPasswordHash() == null)
            throw new IllegalStateException("A password must be assigned before enabling the user");

        if (userRoleRepository.countByUser_Id(userId) == 0)
            throw new IllegalStateException("At least one role is required");

        if (user.isMustChangePassword()
                && (user.getTemporaryPasswordExpiresAt() == null
                || !user.getTemporaryPasswordExpiresAt().isAfter(now())
        ))
            throw new IllegalStateException("The temporary password has expired");

        if (!user.isEnabled()) {
            user.enable();
            user.revokeTokens();
            userRepository.saveAndFlush(user);
        }

        return queryService.findById(userId);
    }

    @Transactional
    public UserDetails disable(Long userId) {
        User user = requireUser(userId);

        if (!user.isEnabled())
            return queryService.findById(userId);

        if (userRoleRepository.existsByUser_IdAndRole_Name(userId, RoleName.ADMINISTRATOR)
                && userRepository.lockEnabledAdministrators().size() <= 1)
            throw new IllegalStateException("The final enabled administrator cannot be disabled");

        user.disable();
        userRepository.saveAndFlush(user);

        return queryService.findById(userId);
    }

    @Transactional
    public UserDetails unlock(Long userId) {
        User user = requireUser(userId);

        user.unlock();
        user.revokeTokens();
        userRepository.saveAndFlush(user);

        return queryService.findById(userId);
    }

    @Transactional
    public TemporaryPasswordDetails resetPassword(Long userId) {
        User user = requireUser(userId);

        GeneratedTemporaryPassword generated = passwordGenerator.generate();
        LocalDateTime expiresAt = now().plus(TEMPORARY_PASSWORD_TTL);

        user.assignPassword(
                passwordEncoder.encode(generated.plaintext()),
                true,
                expiresAt
        );
        user.unlock();
        userRepository.saveAndFlush(user);

        return new TemporaryPasswordDetails(generated.plaintext(), expiresAt);
    }

    private @NonNull User requireUser(Long userId) {
        requireId(userId, "User");

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    @Contract("null -> fail")
    private @NonNull Role requireRole(RoleName roleName) {
        if (roleName == null)
            throw new IllegalArgumentException("Role is required");

        return roleRepository.findByName(roleName)
                .orElseThrow(() -> new IllegalArgumentException("Role does not exist: " + roleName)
                );
    }

    @Contract("null -> fail")
    private @NonNull @Unmodifiable Set<Role> requireRoles(Set<RoleName> roleNames) {
        if (roleNames == null || roleNames.isEmpty())
            throw new IllegalArgumentException("At least one role is required");

        List<Role> roles = roleRepository.findAllByNameIn(roleNames);

        if (roles.size() != roleNames.size())
            throw new IllegalArgumentException("One or more roles do not exist");

        return Set.copyOf(roles);
    }

    private void validateEmail(String email, Long currentUserId) {
        if (email == null || email.isBlank())
            return;

        String normalizedEmail = email.trim().toUpperCase(Locale.ROOT);

        boolean exists = currentUserId == null
                ? userInfoRepository
                    .existsByNormalizedEmail(normalizedEmail)
                : userInfoRepository
                    .existsByNormalizedEmailAndUserIdNot(normalizedEmail, currentUserId);

        if (exists)
            throw new IllegalArgumentException("Email address already exists");
    }

    @Contract(" -> new")
    private @NonNull LocalDateTime now() {
        return LocalDateTime.ofInstant(
                clock.instant(),
                ZoneOffset.UTC
        );
    }
}