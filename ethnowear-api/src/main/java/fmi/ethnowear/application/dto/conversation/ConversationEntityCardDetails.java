package fmi.ethnowear.application.dto.conversation;

import fmi.ethnowear.domain.model.ontology.FeatureType;

public record ConversationEntityCardDetails(
        FeatureType entityType,
        String localName,
        String label,
        Long representativeMediaAssetId
) {

    public ConversationEntityCardDetails(
            FeatureType entityType,
            String localName,
            String label
    ) {
        this(entityType, localName, label, null);
    }
}
