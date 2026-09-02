package fmi.ethnowear.application.conversation.prompt;

import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationArchiveEvidence;
import fmi.ethnowear.application.model.conversation.ConversationOntologyEvidence;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.List;

public record ConversationPromptEvidence(
        List<DocumentEvidence> documents,
        List<ConversationOntologyEvidence> ontology,
        List<ConversationArchiveEvidence> archive
) {

    public ConversationPromptEvidence {
        documents = List.copyOf(documents);
        ontology = List.copyOf(ontology);
        archive = List.copyOf(archive);
    }

    @Contract("_ -> new")
    public static @NonNull ConversationPromptEvidence from(@NonNull ConversationEvidenceBundle evidence) {
        return new ConversationPromptEvidence(
                evidence.documentPassages()
                        .stream()
                        .map(DocumentEvidence::from)
                        .toList(),
                evidence.ontologyEvidence(),
                evidence.archiveEvidence()
        );
    }

    public record DocumentEvidence(String citationId, GroundedPassageDetails passage) {

        @Contract("_ -> new")
        private static @NonNull DocumentEvidence from(@NonNull GroundedPassageDetails passage) {
            return new DocumentEvidence("chunk:" + passage.chunkId(), passage);
        }
    }
}
