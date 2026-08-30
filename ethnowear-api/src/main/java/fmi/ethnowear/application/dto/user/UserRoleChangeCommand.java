package fmi.ethnowear.application.dto.user;

import fmi.ethnowear.domain.model.user.RoleName;
import jakarta.validation.constraints.NotNull;

public record UserRoleChangeCommand(
        @NotNull RoleName role
) {
}