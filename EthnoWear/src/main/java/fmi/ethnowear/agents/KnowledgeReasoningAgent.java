package fmi.ethnowear.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.agents.protocol.AgentMessageTypes;
import fmi.ethnowear.agents.protocol.AnalyzeFeaturesPayload;
import fmi.ethnowear.agents.protocol.ReasoningResultPayload;
import fmi.ethnowear.application.service.RuleBasedReasoningService;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.jspecify.annotations.NonNull;

public class KnowledgeReasoningAgent extends Agent {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RuleBasedReasoningService reasoningService;

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started");

        Object[] args = getArguments();
        if(args == null || args.length == 0 || !(args[0] instanceof RuleBasedReasoningService service)) {
            throw new IllegalStateException("KnowledgeReasoningAgent requires RuleBasedReasoningService");
        }

        this.reasoningService = service;

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                        MessageTemplate.MatchOntology(AgentMessageTypes.ANALYZE_FEATURES)
                );

                ACLMessage message = receive(template);

                if(message == null) {
                    block();
                    return;
                }

                handleAnalyzeFeatures(message);
            }
        });
    }

    private void handleAnalyzeFeatures(@NonNull ACLMessage message) {
        try {
            AnalyzeFeaturesPayload payload = objectMapper.readValue(
                    message.getContent(),
                    AnalyzeFeaturesPayload.class
            );

            ReasoningResultPayload reasoningResult = reasoningService.reason(payload);

            ACLMessage nextMessage = new ACLMessage(ACLMessage.INFORM);
            nextMessage.addReceiver(new AID(AgentNames.INTERPRETATION, AID.ISLOCALNAME));
            nextMessage.setConversationId(message.getConversationId());
            nextMessage.setOntology(AgentMessageTypes.REASONING_RESULT);
            nextMessage.setLanguage("JSON");
            nextMessage.setContent(objectMapper.writeValueAsString(reasoningResult));

            send(nextMessage);
            System.out.println(getLocalName() + " sent reasoning result: " + payload.conversationId());
        } catch (JsonProcessingException e) {
            System.err.println(getLocalName() + " could not process analysis request: " + e.getMessage());
        }
    }
}
