package fmi.ethnowear.application.dto.user;

public record UserProfileDetails(
        String firstName,
        String lastName,
        String email,
        String phone,
        String addressLine1,
        String addressLine2,
        String city,
        String postalCode,
        String countryCode
) {
}