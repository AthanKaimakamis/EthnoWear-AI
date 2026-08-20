package fmi.ethnowear.application.dto.user;

import java.time.LocalDateTime;

public record TemporaryPasswordDetails(
        String temporaryPassword,
        LocalDateTime expiresAt
) {
}