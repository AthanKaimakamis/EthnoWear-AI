package fmi.ethnowear.application.dto.user;

import fmi.ethnowear.domain.model.user.RoleName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.Set;

public record UserCreateCommand(
        @NotBlank
        @Size(min = 3, max = 100)
        String username,

        @NotNull
        @Valid
        UserProfileCommand profile,

        @NotEmpty
        Set<@NotNull RoleName> roles
) {
    public UserCreateCommand {
        roles = roles == null ? null : Set.copyOf(roles);
    }
}