package fmi.ethnowear.application.dto.auth;

import fmi.ethnowear.domain.model.user.RoleName;

import java.util.Set;

public record CurrentUserDetails(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        Set<RoleName> roles,
        boolean passwordChangeRequired
) {
    public CurrentUserDetails {
        roles = Set.copyOf(roles);
    }
}