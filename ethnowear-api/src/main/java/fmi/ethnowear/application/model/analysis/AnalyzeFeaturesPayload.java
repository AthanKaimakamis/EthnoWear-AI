package fmi.ethnowear.application.model.analysis;

public record AnalyzeFeaturesPayload(
        String conversationId,
        SelectedFeaturesPayload selectedFeatures,
        String language
) {
}
