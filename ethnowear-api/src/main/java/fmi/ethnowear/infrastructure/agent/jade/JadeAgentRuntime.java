package fmi.ethnowear.infrastructure.agent.jade;

import fmi.ethnowear.application.service.analysis.RuleBasedReasoningService;
import fmi.ethnowear.application.service.conversation.orchestration.ConversationAgentReasoningService;
import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JadeAgentRuntime implements ApplicationRunner {

    private final RuleBasedReasoningService reasoningService;

    private AgentContainer mainContainer;
    private AgentController clientAgentController;
    private final ConversationAgentReasoningService conversationReasoningService;

    @Override
    public void run(ApplicationArguments args) {
        startPlatform();

        clientAgentController = startAgent(AgentNames.CLIENT, ClientAgent.class);
        startAgent(
                AgentNames.KNOWLEDGE_REASONING,
                KnowledgeReasoningAgent.class,
                reasoningService,
                conversationReasoningService
        );
        startAgent(AgentNames.INTERPRETATION, InterpretationAgent.class);
    }

    public AgentController getClientAgentController() {
        if (clientAgentController == null)
            throw new IllegalStateException("Client JADE agent is not started yet.");

        return clientAgentController;
    }

    private void startPlatform() {
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "false");

        AgentContainer container = runtime.createMainContainer(profile);
        if (container == null)
            throw new IllegalStateException("Could not start the JADE main container. Ensure JADE port 1099 is not already in use.");

        mainContainer = container;
        System.out.println("JADE main container started");
    }

    private @NonNull AgentController startAgent(String name, @NonNull Class<?> agentClass, Object... args) {
        try {
            AgentController controller = mainContainer.createNewAgent(name, agentClass.getName(), args);
            controller.start();
            return controller;
        } catch (StaleProxyException exception) {
            throw new IllegalStateException("Could not start JADE agent: " + name, exception);
        }
    }
}
