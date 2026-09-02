package fmi.ethnowear.application.model.conversation;

import com.fasterxml.jackson.annotation.JsonIgnore;
import fmi.ethnowear.application.dto.conversation.ConversationArchiveCardDetails;
import fmi.ethnowear.application.dto.conversation.ConversationEntityCardDetails;
import fmi.ethnowear.application.dto.conversation.ConversationMediaDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;

import java.util.List;

public record ConversationEvidenceBundle(
        List<GroundedPassageDetails> documentPassages,
        List<ConversationOntologyEvidence> ontologyEvidence,
        List<ConversationArchiveEvidence> archiveEvidence,
        List<ConversationEntityCardDetails> entityCards,
        List<ConversationArchiveCardDetails> archiveCards,
        List<ConversationMediaDetails> media,
        List<String> warningCodes
) {

    public ConversationEvidenceBundle {
        documentPassages = List.copyOf(documentPassages);
        ontologyEvidence = List.copyOf(ontologyEvidence);
        archiveEvidence = List.copyOf(archiveEvidence);
        entityCards = List.copyOf(entityCards);
        archiveCards = List.copyOf(archiveCards);
        media = List.copyOf(media);
        warningCodes = List.copyOf(warningCodes);
    }

    public ConversationEvidenceBundle(
            List<GroundedPassageDetails> documentPassages,
            List<ConversationOntologyEvidence> ontologyEvidence,
            List<ConversationEntityCardDetails> entityCards,
            List<ConversationArchiveCardDetails> archiveCards,
            List<ConversationMediaDetails> media,
            List<String> warningCodes
    ) {
        this(
                documentPassages,
                ontologyEvidence,
                List.of(),
                entityCards,
                archiveCards,
                media,
                warningCodes
        );
    }

    public ConversationEvidenceBundle(
            List<GroundedPassageDetails> documentPassages,
            List<ConversationOntologyEvidence> ontologyEvidence,
            List<ConversationEntityCardDetails> entityCards,
            List<ConversationArchiveCardDetails> archiveCards,
            List<String> warningCodes
    ) {
        this(
                documentPassages,
                ontologyEvidence,
                List.of(),
                entityCards,
                archiveCards,
                List.of(),
                warningCodes
        );
    }

    @JsonIgnore
    public boolean isEmpty() {
        return documentPassages.isEmpty()
                && ontologyEvidence.isEmpty()
                && archiveEvidence.isEmpty();
    }
}
