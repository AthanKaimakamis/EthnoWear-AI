package fmi.ethnowear.application.service.auth;

import fmi.ethnowear.domain.model.user.RoleName;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public record EthnoWearUserPrincipal(
        Long userId,
        String username,
        String password,
        boolean enabled,
        boolean accountNonLocked,
        boolean credentialsNonExpired,
        boolean mustChangePassword,
        int tokenVersion,
        Set<RoleName> roles
) implements UserDetails {

    private static final String ROLE_PREFIX = "ROLE_";

    @Contract("_, _, _, _, _, _, _, _, _, _ -> new")
    public static @NonNull EthnoWearUserPrincipal of(
            Long userId,
            String username,
            String passwordHash,
            boolean enabled,
            LocalDateTime lockedUntil,
            boolean mustChangePassword,
            LocalDateTime temporaryPasswordExpiresAt,
            int tokenVersion,
            Set<RoleName> roles,
            LocalDateTime now
    ) {
        boolean accountNonLocked =
                lockedUntil == null || lockedUntil.isBefore(now);

        boolean credentialsNonExpired =
                !mustChangePassword
                        || temporaryPasswordExpiresAt != null
                        && temporaryPasswordExpiresAt.isAfter(now);

        return new EthnoWearUserPrincipal(
                userId,
                username,
                passwordHash,
                enabled,
                accountNonLocked,
                credentialsNonExpired,
                mustChangePassword,
                tokenVersion,
                Set.copyOf(roles)
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if(mustChangePassword)
            return List.of(
                    new SimpleGrantedAuthority("PASSWORD_CHANGE_REQUIRED")
            );

        return roles.stream()
                .map(RoleName::name)
                .sorted()
                .map(role -> new SimpleGrantedAuthority(ROLE_PREFIX + role))
                .toList();
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return accountNonLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return credentialsNonExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
