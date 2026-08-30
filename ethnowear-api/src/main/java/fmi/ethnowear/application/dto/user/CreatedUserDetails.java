package fmi.ethnowear.application.dto.user;

public record CreatedUserDetails(
        UserDetails user,
        TemporaryPasswordDetails credentials
) {
}