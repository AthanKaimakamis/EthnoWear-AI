package fmi.ethnowear.infrastructure.agent.jade;

import fmi.ethnowear.application.exception.ConversationGenerationUnavailableException;
import fmi.ethnowear.application.model.conversation.ConversationAgentFinding;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationReasoningResult;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.port.conversation.ConversationReasoningGateway;
import fmi.ethnowear.infrastructure.agent.jade.protocol.ConversationReasoningCommand;
import fmi.ethnowear.infrastructure.agent.jade.protocol.ConversationReasoningPayload;
import jade.wrapper.StaleProxyException;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class JadeConversationReasoningGateway implements ConversationReasoningGateway {

    private static final Duration AGENT_TIMEOUT = Duration.ofSeconds(5);
    private static final int MAXIMUM_FINDINGS = 32;

    private final JadeAgentRuntime runtime;

    @Override
    public ConversationReasoningResult reason(
            @NonNull ConversationTurnExecutionContext context,
            @NonNull ConversationEvidenceBundle evidence
    ) {
        CompletableFuture<ConversationReasoningResult> future = new CompletableFuture<>();

        ConversationReasoningPayload payload =
                new ConversationReasoningPayload(
                        context.turnId(),
                        context,
                        evidence
                );

        try {
            runtime.getClientAgentController()
                    .putO2AObject(
                            new ConversationReasoningCommand(
                                    payload,
                                    future
                            ),
                            false
                    );

            ConversationReasoningResult result = future.get(
                    AGENT_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS
            );

            validate(result, evidence);

            return result;
        } catch (StaleProxyException exception) {
            throw unavailable(exception);
        } catch (TimeoutException exception) {
            future.cancel(true);
            throw unavailable(exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable(exception);
        } catch (ExecutionException exception) {
            throw unavailable(exception.getCause());
        }
    }

    private void validate(ConversationReasoningResult result, ConversationEvidenceBundle evidence) {
        if (result == null || result.findings().size() > MAXIMUM_FINDINGS)
            throw unavailable(null);

        Set<String> allowedEvidence = allowedEvidence(evidence);

        boolean invalidFinding = result.findings()
                .stream()
                .anyMatch(finding -> invalidFinding(finding, allowedEvidence));

        if (invalidFinding)
            throw unavailable(null);

        boolean invalidWarning = result.warningCodes()
                .stream()
                .anyMatch(code -> code == null || !code.matches("[A-Z0-9_]{1,100}"));

        if (invalidWarning)
            throw unavailable(null);
    }

    private boolean invalidFinding(ConversationAgentFinding finding, Set<String> allowedEvidence) {
        return finding == null
                || finding.statement().length() > 1500
                || finding.supportingEvidenceIds().isEmpty()
                || finding.supportingEvidenceIds()
                .stream()
                .anyMatch(id -> id == null || !allowedEvidence.contains(id));
    }

    private @NonNull @Unmodifiable Set<String> allowedEvidence(@NonNull ConversationEvidenceBundle evidence) {
        Set<String> result = new LinkedHashSet<>();

        evidence.documentPassages().forEach(
                passage -> result.add("chunk:" + passage.chunkId())
        );

        evidence.ontologyEvidence().forEach(
                item -> result.add(item.citationId())
        );

        return Set.copyOf(result);
    }

    @Contract("_ -> new")
    private @NonNull ConversationGenerationUnavailableException unavailable(Throwable cause) {
        return new ConversationGenerationUnavailableException("Conversation reasoning service is unavailable", cause);
    }
}
