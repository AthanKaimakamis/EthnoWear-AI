package fmi.ethnowear.agents;

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
public class JadePlatformService implements ApplicationRunner {

    private AgentContainer mainContainer;

    @Override
    public void run(ApplicationArguments args) {
        startPlatform();
        startAgent(AgentNames.CLIENT, ClientAgent.class);
        startAgent(AgentNames.KNOWLEDGE_REASONING, KnowledgeReasoningAgent.class);
        startAgent(AgentNames.INTERPRETATION, InterpretationAgent.class);
    }

    private void startPlatform() {
        Runtime runtime = Runtime.instance();
        Profile profile = new ProfileImpl();
        profile.setParameter(Profile.MAIN_HOST, "localhost");
        profile.setParameter(Profile.GUI, "false");

        mainContainer = runtime.createMainContainer(profile);
        System.out.println("JADE main container started");
    }

    private void startAgent(String name, Class<?> agentClass) {
        try {
            AgentController controller = mainContainer.createNewAgent(name, agentClass.getName(), null);
            controller.start();
        } catch (StaleProxyException exception) {
            throw new IllegalStateException("Could not start JADE agent: " + name, exception);
        }
    }
}
