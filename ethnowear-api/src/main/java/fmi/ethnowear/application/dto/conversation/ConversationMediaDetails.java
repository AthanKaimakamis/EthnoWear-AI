package fmi.ethnowear.application.dto.conversation;

import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.ontology.FeatureType;

public record ConversationMediaDetails(
        Long mediaAssetId,
        MediaType mediaType,
        String caption,
        String contentUrl,
        Long archiveItemId,
        FeatureType entityType,
        String entityLocalName
) {
}