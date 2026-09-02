package fmi.ethnowear.infrastructure.agent.jade;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.model.analysis.AnalyzeFeaturesPayload;
import fmi.ethnowear.application.model.analysis.ReasoningResultPayload;
import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;
import fmi.ethnowear.application.service.conversation.orchestration.ConversationAgentReasoningService;
import fmi.ethnowear.application.service.analysis.RuleBasedReasoningService;
import fmi.ethnowear.infrastructure.agent.jade.protocol.AgentMessageTypes;
import fmi.ethnowear.infrastructure.agent.jade.protocol.ConversationReasoningPayload;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import org.jspecify.annotations.NonNull;

public class KnowledgeReasoningAgent extends Agent {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private RuleBasedReasoningService reasoningService;
    private ConversationAgentReasoningService conversationReasoningService;

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started");

        Object[] args = getArguments();

        if (args == null
                || args.length < 2
                || !(args[0] instanceof RuleBasedReasoningService analysisService)
                || !(args[1] instanceof ConversationAgentReasoningService conversationService)) {
            throw new IllegalStateException("KnowledgeReasoningAgent requires both reasoning services");
        }

        this.reasoningService = analysisService;
        this.conversationReasoningService = conversationService;

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                MessageTemplate template = MessageTemplate.and(
                        MessageTemplate.MatchPerformative(ACLMessage.REQUEST),
                        MessageTemplate.or(
                                MessageTemplate.MatchOntology(AgentMessageTypes.ANALYZE_FEATURES),
                                MessageTemplate.MatchOntology(AgentMessageTypes.CONVERSATION_REASONING_REQUEST)
                        )
                );

                ACLMessage message = receive(template);

                if (message == null) {
                    block();
                    return;
                }

                if (AgentMessageTypes.ANALYZE_FEATURES.equals(message.getOntology()))
                    handleAnalyzeFeatures(message);
                else
                    handleConversationReasoning(message);
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

    private void handleConversationReasoning(@NonNull ACLMessage message) {
        try {
            ConversationReasoningPayload payload = objectMapper.readValue(
                    message.getContent(),
                    ConversationReasoningPayload.class
            );

            ConversationReasoningResult result = conversationReasoningService.reason(payload);

            ACLMessage nextMessage = new ACLMessage(ACLMessage.INFORM);
            nextMessage.addReceiver(new AID(
                    AgentNames.INTERPRETATION,
                    AID.ISLOCALNAME
            ));
            nextMessage.setConversationId(message.getConversationId());
            nextMessage.setOntology(AgentMessageTypes.CONVERSATION_REASONING_RESULT);
            nextMessage.setLanguage("JSON");
            nextMessage.setContent(objectMapper.writeValueAsString(result));

            send(nextMessage);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not process conversation reasoning request", ex);
        }
    }
}
