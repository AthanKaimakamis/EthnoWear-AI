package fmi.ethnowear.application.dto.ontology.admin;

public record RegionDerivedTypeSynchronizationDetails(
        int created,
        int updated,
        int unchanged
) {
    public RegionDerivedTypeSynchronizationDetails add(
            RegionDerivedTypeSynchronizationDetails other
    ) {
        return new RegionDerivedTypeSynchronizationDetails(
                created + other.created,
                updated + other.updated,
                unchanged + other.unchanged
        );
    }

    public static RegionDerivedTypeSynchronizationDetails empty() {
        return new RegionDerivedTypeSynchronizationDetails(0, 0, 0);
    }
}
