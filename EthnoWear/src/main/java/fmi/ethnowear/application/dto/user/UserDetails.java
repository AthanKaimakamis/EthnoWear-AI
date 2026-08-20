package fmi.ethnowear.application.dto.user;

import fmi.ethnowear.domain.model.user.RoleName;

import java.time.LocalDateTime;
import java.util.Set;

public record UserDetails(
        Long id,
        String username,
        UserProfileDetails profile,
        Set<RoleName> roles,
        boolean enabled,
        boolean passwordChangeRequired,
        LocalDateTime temporaryPasswordExpiresAt,
        LocalDateTime lockedUntil,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public UserDetails {
        roles = Set.copyOf(roles);
    }
}