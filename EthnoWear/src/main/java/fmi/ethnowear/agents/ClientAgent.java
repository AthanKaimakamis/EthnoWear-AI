package fmi.ethnowear.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.agents.protocol.*;
import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;

public class ClientAgent extends Agent {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void setup() {
        System.out.println(getLocalName() + " started");

        setEnabledO2ACommunication(true, 0);

        addBehaviour(new CyclicBehaviour(this) {
            @Override
            public void action() {
                Object object = getO2AObject();

                if(object == null){
                    block(100);
                    return;
                }

                if(object instanceof AnalysisCommand command){
                    addBehaviour(new AnalysisRequestBehaviour(command));
                }
            }
        });
    }

    private class AnalysisRequestBehaviour extends Behaviour {

        private final AnalysisCommand command;
        private MessageTemplate responseTemplate;
        private int step = 0;

        private AnalysisRequestBehaviour(AnalysisCommand command){
            this.command = command;
        }

        @Override
        public void action() {
            switch (step){
                case 0 -> sendAnalysisRequest();
                case 1 -> receiveInterpretationResult();
                default -> {
                }
            }
        }

        @Override
        public boolean done() {
            return step == 2;
        }

        private void sendAnalysisRequest() {
            try {
                ACLMessage request = new ACLMessage(ACLMessage.REQUEST);
                request.addReceiver(new AID(AgentNames.KNOWLEDGE_REASONING, AID.ISLOCALNAME));
                request.setConversationId(command.payload().conversationId());
                request.setOntology(AgentMessageTypes.ANALYZE_FEATURES);
                request.setLanguage("JSON");
                request.setContent(objectMapper.writeValueAsString(command.payload()));

                responseTemplate = MessageTemplate.and(
                        MessageTemplate.MatchConversationId(command.payload().conversationId()),
                        MessageTemplate.MatchOntology(AgentMessageTypes.INTERPRETATION_RESULT)
                );

                send(request);
                System.out.println(getLocalName() + " sent analysis request: " + command.payload().conversationId());
                step = 1;
            } catch (JsonProcessingException e){
                command.result().completeExceptionally(e);
                step = 2;
            }
        }

        private void receiveInterpretationResult() {
            ACLMessage reply = receive(responseTemplate);

            if(reply == null) {
                block();
                return;
            }

            try {
                InterpretationResultPayload result = objectMapper.readValue(
                        reply.getContent(),
                        InterpretationResultPayload.class
                );

                command.result().complete(result);
                System.out.println(getLocalName() + " received interpretation result: " + result.conversationId());
            } catch (JsonProcessingException e) {
                command.result().completeExceptionally(e);
            }
            step = 2;
        }
    }
}
