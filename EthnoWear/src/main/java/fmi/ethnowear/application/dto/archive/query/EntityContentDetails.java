package fmi.ethnowear.application.dto.archive.query;

import fmi.ethnowear.domain.model.ontology.FeatureType;

import java.util.List;

public record EntityContentDetails(
        FeatureType entityType,
        String ontologyIri,
        List<EntityKnowledgeChunkDetails> knowledgeChunks,
        List<EntityMediaAnnotationDetails> mediaAnnotations,
        List<EntitySourceCitationDetails> sources
) {
}