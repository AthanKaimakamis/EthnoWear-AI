package fmi.ethnowear.application.service.conversation.orchestration;

import fmi.ethnowear.application.model.conversation.ConversationAgentFinding;
import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;
import fmi.ethnowear.domain.model.conversation.ConversationAgentRole;
import fmi.ethnowear.infrastructure.agent.jade.protocol.ConversationReasoningPayload;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

import static fmi.ethnowear.util.TextUtils.normalizeSearchText;

@Service
public class ConversationAgentReasoningService {

    private static final int MAXIMUM_FINDINGS = 24;

    public ConversationReasoningResult reason(@NonNull ConversationReasoningPayload payload) {
        List<ConversationAgentFinding> findings = new ArrayList<>();

        payload.evidence().ontologyEvidence().forEach(item ->
                findings.add(new ConversationAgentFinding(
                        ConversationAgentRole.ONTOLOGY_SPECIALIST,
                        ontologyStatement(
                                item.label(),
                                item.description(),
                                item.relationships()
                        ),
                        List.of(item.citationId()),
                        false
                ))
        );

        payload.evidence().documentPassages().forEach(passage ->
                findings.add(new ConversationAgentFinding(
                        ConversationAgentRole.DOCUMENT_EVIDENCE_SPECIALIST,
                        "Approved document evidence is available from “"
                                + passage.documentTitle() + "”.",
                        List.of("chunk:" + passage.chunkId()),
                        false
                ))
        );

        payload.evidence().ontologyEvidence().forEach(ontology ->
                payload.evidence().documentPassages().stream()
                        .filter(passage -> mentions(
                                passage.excerpt(),
                                ontology.label()
                        ))
                        .forEach(passage ->
                                findings.add(new ConversationAgentFinding(
                                        ConversationAgentRole.INTERPRETATION_SPECIALIST,
                                        "The approved document evidence explicitly mentions "
                                                + "the ontology concept “"
                                                + ontology.label() + "”.",
                                        List.of(
                                                ontology.citationId(),
                                                "chunk:" + passage.chunkId()
                                        ),
                                        false
                                ))
                        )
        );

        List<String> warnings = findings.isEmpty()
                ? List.of("AGENT_EVIDENCE_NOT_FOUND")
                : List.of();

        return new ConversationReasoningResult(
                findings.stream()
                        .limit(MAXIMUM_FINDINGS)
                        .toList(),
                warnings
        );
    }

    private String ontologyStatement(String label, String description, @NonNull List<String> relationships) {
        String statement = description == null || description.isBlank()
                ? "Ontology concept: " + label + "." : label + ": " + description.trim();

        if (!relationships.isEmpty())
            statement += " Related concepts: " + String.join("; ", relationships) + ".";

        return statement.length() <= 1500
                ? statement
                : statement.substring(0, 1500);
    }

    private boolean mentions(String text, String conceptLabel) {
        String normalizedText = normalizeSearchText(text);
        String normalizedLabel = normalizeSearchText(conceptLabel);

        return normalizedLabel.length() >= 4
                && (" " + normalizedText + " ")
                .contains(" " + normalizedLabel + " ");
    }
}
