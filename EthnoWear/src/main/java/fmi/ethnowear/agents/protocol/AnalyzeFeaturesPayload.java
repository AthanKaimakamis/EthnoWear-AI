package fmi.ethnowear.agents.protocol;

public record AnalyzeFeaturesPayload(
        String conversationId,
        SelectedFeaturesPayload selectedFeatures,
        String language
) {
}
