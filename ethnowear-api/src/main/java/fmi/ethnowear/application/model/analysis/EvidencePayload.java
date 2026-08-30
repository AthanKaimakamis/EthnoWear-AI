package fmi.ethnowear.application.model.analysis;

public record EvidencePayload(
        String featureType,
        String selectedFeature,
        String matchedProperty,
        int weight
) {
}
