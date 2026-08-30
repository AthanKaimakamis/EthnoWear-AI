package fmi.ethnowear.application.dto.user;

import jakarta.validation.constraints.*;

public record UserProfileCommand(
        @NotBlank @Size(max = 100) String firstName,
        @NotBlank @Size(max = 100) String lastName,
        @Email @Size(max = 320) String email,
        @Size(max = 50) String phone,
        @Size(max = 250) String addressLine1,
        @Size(max = 250) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 20) String postalCode,
        @Pattern(
                regexp = "^[A-Za-z]{2}$",
                message = "Country code must contain two letters"
        )
        String countryCode
) {
}
