package fmi.ethnowear.application.dto.publicauth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PublicGoogleLoginCommand(@NotBlank @Size(max = 8192) String credential) {
    @Override
    public String toString() {
        return "PublicGoogleLoginCommand[credential=REDACTED]";
    }
}
