package fmi.ethnowear.domain.model.conversation;

import fmi.ethnowear.domain.model.ontology.FeatureType;

public enum ConversationArchiveTarget {
    REGIONAL_EMBROIDERY(FeatureType.REGIONAL_EMBROIDERY),
    REGIONAL_MOTIF(FeatureType.REGIONAL_MOTIF),
    MOTIF(FeatureType.MOTIF),
    TECHNIQUE(FeatureType.TECHNIQUE),
    ORNAMENT(FeatureType.ORNAMENT);

    private final FeatureType featureType;

    ConversationArchiveTarget(FeatureType featureType) {
        this.featureType = featureType;
    }

    public FeatureType featureType() {
        return featureType;
    }
}
