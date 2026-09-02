package fmi.ethnowear.application.service.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationOntologyEvidence;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.service.conversation.orchestration.ConversationAgentReasoningService;
import fmi.ethnowear.domain.model.conversation.ConversationAgentRole;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.infrastructure.agent.jade.protocol.ConversationReasoningPayload;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationAgentReasoningServiceTest {

    private final ConversationAgentReasoningService service =
            new ConversationAgentReasoningService();

    @Test
    void producesGroundedSpecialistAndCrossEvidenceFindings() {
        UUID conversationId = UUID.randomUUID();
        UUID turnId = UUID.randomUUID();
        ConversationOntologyEvidence ontology = new ConversationOntologyEvidence(
                "ontology:TECHNIQUE:ChainTechnique",
                FeatureType.TECHNIQUE,
                "https://example.org/ChainTechnique",
                "ChainTechnique",
                "синджир бод",
                "Техника за бродиране",
                List.of()
        );
        GroundedPassageDetails passage = new GroundedPassageDetails(
                17L,
                "Прилага се синджир бод.",
                "bg",
                null,
                9L,
                "Източник",
                List.of(),
                0.8,
                null,
                null,
                null,
                false
        );
        ConversationEvidenceBundle evidence = new ConversationEvidenceBundle(
                List.of(passage),
                List.of(ontology),
                List.of(),
                List.of(),
                List.of()
        );

        var result = service.reason(new ConversationReasoningPayload(
                turnId,
                new ConversationTurnExecutionContext(
                        conversationId,
                        turnId,
                        "bg",
                        "Какво е синджир бод?"
                ),
                evidence
        ));

        assertThat(result.findings())
                .extracting(finding -> finding.role())
                .containsExactly(
                        ConversationAgentRole.ONTOLOGY_SPECIALIST,
                        ConversationAgentRole.DOCUMENT_EVIDENCE_SPECIALIST,
                        ConversationAgentRole.INTERPRETATION_SPECIALIST
                );
        assertThat(result.findings().getLast().supportingEvidenceIds())
                .containsExactly(
                        "ontology:TECHNIQUE:ChainTechnique",
                        "chunk:17"
                );
    }

    @Test
    void jadePayloadRoundTripsWithoutDerivedEmptyProperty() throws Exception {
        var mapper = new ObjectMapper();
        UUID requestId = UUID.randomUUID();
        var payload = new ConversationReasoningPayload(
                requestId,
                new ConversationTurnExecutionContext(
                        UUID.randomUUID(),
                        requestId,
                        "bg",
                        "Покажи орнаменти"
                ),
                new ConversationEvidenceBundle(
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
                )
        );

        String json = mapper.writeValueAsString(payload);

        assertThat(json).doesNotContain("\"empty\"");
        assertThat(mapper.readValue(json, ConversationReasoningPayload.class))
                .isEqualTo(payload);
    }
}
