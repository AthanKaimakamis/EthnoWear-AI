package fmi.ethnowear.application.model.conversation;

import fmi.ethnowear.domain.model.ontology.FeatureType;

import java.util.List;

public record ConversationOntologyEvidence(
        String citationId,
        FeatureType entityType,
        String iri,
        String localName,
        String label,
        String description,
        List<String> relationships
) {

    public ConversationOntologyEvidence {
        relationships = List.copyOf(relationships);
    }
}
