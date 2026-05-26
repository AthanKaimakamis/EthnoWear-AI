package fmi.ethnowear.application.service.agent;

import fmi.ethnowear.agents.protocol.*;
import jade.wrapper.StaleProxyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class AgentAnalysisGatewayImpl implements AgentAnalysisGateway{

    private static final Duration AGENT_TIMEOUT = Duration.ofSeconds(5);

    private final AgentRunnerService agentRunnerService;

    public AgentAnalysisGatewayImpl(AgentRunnerService agentRunnerService) {
        this.agentRunnerService = agentRunnerService;
    }

    @Override
    public InterpretationResultPayload analyze(AnalyzeFeaturesPayload payload) {
        CompletableFuture<InterpretationResultPayload> result = new CompletableFuture<>();
        AnalysisCommand command = new AnalysisCommand(payload, result);

        try {
            agentRunnerService.getClientAgentController().putO2AObject(command, false);
            return result.get(AGENT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (StaleProxyException exception) {
            throw new IllegalStateException("Could not send analysis command to ClientAgent.", exception);
        } catch (TimeoutException exception) {
            throw new IllegalStateException("Timed out waiting for JADE analysis result: " + payload.conversationId(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for JADE analysis result.", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("JADE analysis failed.", exception);
        }
    }
}
