package fmi.ethnowear.infrastructure.agent.jade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.infrastructure.agent.jade.protocol.AgentMessageTypes;
import fmi.ethnowear.application.model.analysis.CandidatePayload;
import fmi.ethnowear.application.model.analysis.InterpretationResultPayload;
import fmi.ethnowear.application.model.analysis.ReasoningResultPayload;
import fmi.ethnowear.application.model.conversation.ConversationAgentFinding;
import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;
import fmi.ethnowear.domain.model.conversation.ConversationAgentRole;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.ArrayList;

public class InterpretationAgent extends Agent {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started");

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.INFORM),
                        MessageTemplate.or(
                                MessageTemplate.MatchOntology(AgentMessageTypes.REASONING_RESULT),
                                MessageTemplate.MatchOntology(AgentMessageTypes.CONVERSATION_REASONING_RESULT)
                        )
                );

                ACLMessage message = receive(template);

                if (message == null) {
                    block();
                    return;
                }

                if (AgentMessageTypes.REASONING_RESULT.equals(message.getOntology()))
                    handleReasoningResult(message);
                else
                    handleConversationReasoningResult(message);
            }
        });
    }

    private void handleReasoningResult(@NonNull ACLMessage message) {
        try {
            ReasoningResultPayload reasoningResult = objectMapper.readValue(
                    message.getContent(),
                    ReasoningResultPayload.class
            );

            InterpretationResultPayload interpretationResult = createInterpretationResult(reasoningResult);

            ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
            reply.addReceiver(new AID(AgentNames.CLIENT, AID.ISLOCALNAME));
            reply.setConversationId(reasoningResult.conversationId());
            reply.setOntology(AgentMessageTypes.INTERPRETATION_RESULT);
            reply.setLanguage("JSON");
            reply.setContent(objectMapper.writeValueAsString(interpretationResult));

            send(reply);
            System.out.println(getLocalName() + " sent interpretation result: " + reasoningResult.conversationId());
        } catch (JsonProcessingException e) {
            System.err.println(getLocalName() + " could not process reasoning result: " + e.getMessage());
        }
    }

    private void handleConversationReasoningResult(@NonNull ACLMessage message) {
        try {
            ConversationReasoningResult reasoning = objectMapper.readValue(
                    message.getContent(),
                    ConversationReasoningResult.class
            );

            ConversationReasoningResult interpreted = interpret(reasoning);

            ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
            reply.addReceiver(new AID(
                    AgentNames.CLIENT,
                    AID.ISLOCALNAME
            ));
            reply.setConversationId(message.getConversationId());
            reply.setOntology(
                    AgentMessageTypes.CONVERSATION_INTERPRETATION_RESULT
            );
            reply.setLanguage("JSON");
            reply.setContent(
                    objectMapper.writeValueAsString(interpreted)
            );

            send(reply);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not process conversation interpretation", ex);
        }
    }

    private @NonNull ConversationReasoningResult interpret(@NonNull ConversationReasoningResult reasoning) {
        boolean hasOntology = reasoning.findings().stream()
                .anyMatch(finding ->
                        finding.role() == ConversationAgentRole.ONTOLOGY_SPECIALIST
                );

        boolean hasDocuments = reasoning.findings().stream()
                .anyMatch(finding ->
                        finding.role() == ConversationAgentRole.DOCUMENT_EVIDENCE_SPECIALIST
                );

        if (!hasOntology || !hasDocuments)
            return reasoning;

        List<String> evidenceIds = reasoning.findings()
                .stream()
                .flatMap(finding -> finding.supportingEvidenceIds().stream())
                .distinct()
                .limit(20)
                .toList();

        List<ConversationAgentFinding> findings = new ArrayList<>(reasoning.findings());

        findings.add(new ConversationAgentFinding(
                ConversationAgentRole.INTERPRETATION_SPECIALIST,
                "Ontology and approved document evidence may be combined. "
                        + "Conclusions not stated explicitly by the evidence "
                        + "must be identified as interpretations.",
                evidenceIds,
                true
        ));

        return new ConversationReasoningResult(findings, reasoning.warningCodes());
    }

    @Contract("_ -> new")
    private @NonNull InterpretationResultPayload createInterpretationResult(@NonNull ReasoningResultPayload reasoningResult) {
        CandidatePayload topCandidate = reasoningResult.candidates().isEmpty()
                ? null
                : reasoningResult.candidates().getFirst();

        String explanation = topCandidate == null
                ? "No matching embroidery interpretation could be produced from the selected features."
                : "The selected features currently match " + topCandidate.id()
                + " with a score of " + topCandidate.score()
                + " based on the available reasoning evidence.";

        return new InterpretationResultPayload(
                reasoningResult.conversationId(),
                reasoningResult.candidates(),
                explanation,
                List.of("Temporary interpretation until ontology-backed explanation is connected.")
        );
    }
}
