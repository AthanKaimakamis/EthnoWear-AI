package fmi.ethnowear.application.model.publicauth;

import java.util.UUID;

public record PublicUserPrincipal(long userId, UUID publicId, long sessionId) {
}
