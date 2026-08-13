package fmi.ethnowear.api.dto.archive.query;

import fmi.ethnowear.application.enums.FeatureType;

import java.util.List;

public record EntityContentDetails(
        FeatureType entityType,
        String ontologyIri,
        List<EntityKnowledgeChunkDetails> knowledgeChunks,
        List<EntityMediaAnnotationDetails> mediaAnnotations,
        List<EntitySourceCitationDetails> sources
) {
}