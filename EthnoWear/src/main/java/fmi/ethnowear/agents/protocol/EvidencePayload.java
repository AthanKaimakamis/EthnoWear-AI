package fmi.ethnowear.agents.protocol;

public record EvidencePayload(
        String featureType,
        String selectedFeature,
        String matchedProperty,
        int weight
) {
}
