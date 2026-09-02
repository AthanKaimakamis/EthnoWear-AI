package fmi.ethnowear.application.model.conversation;

import fmi.ethnowear.application.dto.conversation.ConversationArchiveCardDetails;

import java.util.List;

public record ConversationArchiveEvidenceSelection(
        List<ConversationArchiveEvidence> evidence,
        List<ConversationArchiveCardDetails> cards
) {

    public ConversationArchiveEvidenceSelection {
        evidence = List.copyOf(evidence);
        cards = List.copyOf(cards);
    }

    public static ConversationArchiveEvidenceSelection empty() {
        return new ConversationArchiveEvidenceSelection(List.of(), List.of());
    }
}
