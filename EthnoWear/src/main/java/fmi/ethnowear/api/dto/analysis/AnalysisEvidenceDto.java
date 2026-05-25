package fmi.ethnowear.api.dto.analysis;

public record AnalysisEvidenceDto(
        String featureType,
        String selectedFeature,
        String selectedFeatureLabel,
        String matchedProperty,
        String matchedPropertyLabel,
        int weight
) {
}
