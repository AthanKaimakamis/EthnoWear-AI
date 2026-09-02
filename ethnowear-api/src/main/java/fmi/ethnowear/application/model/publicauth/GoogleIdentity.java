package fmi.ethnowear.application.model.publicauth;

public record GoogleIdentity(String issuer, String subject, String email, String displayName) {
}
