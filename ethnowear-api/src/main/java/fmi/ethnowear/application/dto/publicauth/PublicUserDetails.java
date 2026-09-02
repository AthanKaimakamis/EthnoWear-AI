package fmi.ethnowear.application.dto.publicauth;

import java.util.UUID;

public record PublicUserDetails(UUID userId, String displayName, String email) {
}
