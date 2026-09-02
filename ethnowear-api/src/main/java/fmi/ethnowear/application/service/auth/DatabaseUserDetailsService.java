package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.domain.model.user.RoleName;
import fmi.ethnowear.persistence.jpa.entity.user.User;
import fmi.ethnowear.persistence.jpa.repository.user.UserRepository;
import fmi.ethnowear.persistence.jpa.repository.user.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.userdetails.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;
    private final Clock clock;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedUsername = normalize(username);

        User user = userRepository
                .findByNormalizedUsernameAndDeletedAtIsNull(normalizedUsername)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid username or password"));

        Set<RoleName> roles = userRoleRepository
                .findWithRolesByUserId(user.getId())
                .stream()
                .map(assignment -> assignment.getRole().getName())
                .collect(Collectors.toUnmodifiableSet());

        return EthnoWearUserPrincipal.of(
                user.getId(),
                user.getUsername(),
                Objects.requireNonNullElse(user.getPasswordHash(), ""),
                user.isEnabled(),
                user.getLockedUntil(),
                user.isMustChangePassword(),
                user.getTemporaryPasswordExpiresAt(),
                user.getTokenVersion(),
                roles,
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)
        );
    }

    @Contract("null -> fail")
    private @NonNull String normalize(String username) {
        if(username == null || username.isBlank())
            throw new UsernameNotFoundException("Invalid username or password");

        return username.trim().toUpperCase(Locale.ROOT);
    }
}
