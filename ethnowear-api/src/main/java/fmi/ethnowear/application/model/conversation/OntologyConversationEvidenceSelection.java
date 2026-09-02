package fmi.ethnowear.application.model.conversation;

import fmi.ethnowear.application.dto.conversation.ConversationEntityCardDetails;

import java.util.List;

public record OntologyConversationEvidenceSelection(
        List<ConversationOntologyEvidence> evidence,
        List<ConversationEntityCardDetails> entityCards
) {

    public OntologyConversationEvidenceSelection {
        evidence = List.copyOf(evidence);
        entityCards = List.copyOf(entityCards);
    }
}