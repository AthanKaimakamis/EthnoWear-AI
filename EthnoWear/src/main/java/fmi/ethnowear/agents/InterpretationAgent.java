package fmi.ethnowear.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.agents.protocol.AgentMessageTypes;
import fmi.ethnowear.agents.protocol.CandidatePayload;
import fmi.ethnowear.agents.protocol.InterpretationResultPayload;
import fmi.ethnowear.agents.protocol.ReasoningResultPayload;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

import java.util.List;

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
                        MessageTemplate.MatchOntology(AgentMessageTypes.REASONING_RESULT)
                );

                ACLMessage message = receive(template);

                if (message == null) {
                    block();
                    return;
                }

                handleReasoningResult(message);
            }
        });
    }

    private void handleReasoningResult(ACLMessage message) {
        try{
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

    private InterpretationResultPayload createInterpretationResult(ReasoningResultPayload reasoningResult) {
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
