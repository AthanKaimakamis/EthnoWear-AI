package fmi.ethnowear.infrastructure.agent.jade;

import fmi.ethnowear.infrastructure.agent.jade.protocol.AnalysisCommand;
import fmi.ethnowear.application.model.analysis.AnalyzeFeaturesPayload;
import fmi.ethnowear.application.model.analysis.InterpretationResultPayload;
import fmi.ethnowear.application.port.analysis.AnalysisGateway;
import jade.wrapper.StaleProxyException;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
public class JadeAnalysisGateway implements AnalysisGateway {

    private static final Duration AGENT_TIMEOUT = Duration.ofSeconds(5);

    private final JadeAgentRuntime agentRunnerService;

    public JadeAnalysisGateway(JadeAgentRuntime agentRunnerService) {
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
        } catch (TimeoutException ex) {
            throw new IllegalStateException("Timed out waiting for JADE analysis result: " + payload.conversationId(), ex);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for JADE analysis result.", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("JADE analysis failed.", exception);
        }
    }
}
