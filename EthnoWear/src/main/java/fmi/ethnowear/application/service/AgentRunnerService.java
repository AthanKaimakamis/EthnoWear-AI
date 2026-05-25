package fmi.ethnowear.application.service;

import fmi.ethnowear.agents.AgentNames;
import fmi.ethnowear.agents.ClientAgent;
import fmi.ethnowear.agents.InterpretationAgent;
import fmi.ethnowear.agents.KnowledgeReasoningAgent;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import jade.core.Profile;
import jade.core.ProfileImpl;
import jade.core.Runtime;
import jade.wrapper.AgentContainer;
import jade.wrapper.AgentController;
import jade.wrapper.StaleProxyException;

@Service
public class AgentRunnerService implements ApplicationRunner {

    private final RuleBasedReasoningService reasoningService;

    private AgentContainer mainContainer;
    private AgentController clientAgentController;

    public AgentRunnerService(RuleBasedReasoningService reasoningService) {
        this.reasoningService = reasoningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        startPlatform();

        clientAgentController = startAgent(AgentNames.CLIENT, ClientAgent.class);
        startAgent(AgentNames.KNOWLEDGE_REASONING, KnowledgeReasoningAgent.class, reasoningService);
        startAgent(AgentNames.INTERPRETATION, InterpretationAgent.class);
    }

    public AgentController getClientAgentController() {
        if (clientAgentController == null) {
            throw new IllegalStateException("Client JADE agent is not started yet.");
        }

        return clientAgentController;
    }

    private void startPlatform() {
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "false");

        mainContainer = runtime.createMainContainer(profile);
        System.out.println("JADE main container started");
    }

    private AgentController startAgent(String name, Class<?> agentClass, Object... args) {
        try {
            AgentController controller = mainContainer.createNewAgent(name, agentClass.getName(), args);
            controller.start();
            return controller;
        } catch (StaleProxyException exception) {
            throw new IllegalStateException("Could not start JADE agent: " + name, exception);
        }
    }
}
