package fmi.ethnowear.application.dto.auth;

import java.time.Instant;

public record AdminTokenDetails(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        boolean passwordChangeRequired
) {
}
