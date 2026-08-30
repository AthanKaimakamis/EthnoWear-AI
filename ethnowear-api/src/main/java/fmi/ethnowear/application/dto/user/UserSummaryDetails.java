package fmi.ethnowear.application.dto.user;

import fmi.ethnowear.domain.model.user.RoleName;

import java.util.Set;

public record UserSummaryDetails(
        Long id,
        String username,
        String firstName,
        String lastName,
        String email,
        boolean enabled,
        boolean passwordChangeRequired,
        Set<RoleName> roles
) {
    public UserSummaryDetails {
        roles = Set.copyOf(roles);
    }
}