package fmi.ethnowear.application.dto.publicauth;

import java.time.Instant;

public record PublicLoginChallengeDetails(String nonce, Instant expiresAt) {
}
