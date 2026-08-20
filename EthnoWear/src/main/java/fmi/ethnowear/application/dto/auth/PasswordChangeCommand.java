package fmi.ethnowear.application.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordChangeCommand(
        @NotBlank String currentPassword,
        @NotBlank
        @Size(min = 12, max = 128)
        String newPassword
) {
}
