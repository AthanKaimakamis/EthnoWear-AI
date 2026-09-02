package fmi.ethnowear.domain.model.rights;

public enum RightsStatus {
    UNKNOWN,
    PUBLIC_DOMAIN,
    LICENSED,
    RESTRICTED;

    public boolean permitsPublicDisplay() {
        return this == PUBLIC_DOMAIN || this == LICENSED;
    }

    public boolean requiresLicense() {
        return this == LICENSED;
    }
}
