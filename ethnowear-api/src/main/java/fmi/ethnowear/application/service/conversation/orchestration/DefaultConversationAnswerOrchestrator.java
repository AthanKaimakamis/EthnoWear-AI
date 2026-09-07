package fmi.ethnowear.application.service.conversation.orchestration;

import fmi.ethnowear.application.dto.conversation.ConversationAnswerDetails;
import fmi.ethnowear.application.exception.ConversationGenerationUnavailableException;
import fmi.ethnowear.application.exception.ConversationGenerationRejectedException;
import fmi.ethnowear.application.model.conversation.ConversationEvidenceBundle;
import fmi.ethnowear.application.model.conversation.ConversationGenerationRequest;
import fmi.ethnowear.application.model.conversation.ConversationGenerationResult;
import fmi.ethnowear.application.model.conversation.ConversationTurnExecutionContext;
import fmi.ethnowear.application.port.conversation.ConversationAnswerOrchestrator;
import fmi.ethnowear.application.port.conversation.ConversationEvidenceCollector;
import fmi.ethnowear.application.port.conversation.ConversationGenerationGateway;
import fmi.ethnowear.application.port.conversation.ConversationReasoningGateway;
import fmi.ethnowear.application.service.conversation.generation.ConversationAnswerAssembler;
import fmi.ethnowear.application.service.conversation.evidence.ConversationEvidenceAuditService;
import fmi.ethnowear.application.service.conversation.generation.ConversationFallbackAnswerFactory;
import fmi.ethnowear.application.service.conversation.turn.ConversationHistoryService;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
@ConditionalOnBean({
        ConversationEvidenceCollector.class,
        ConversationReasoningGateway.class
})
public class DefaultConversationAnswerOrchestrator implements ConversationAnswerOrchestrator {

    private final ConversationEvidenceCollector evidenceCollector;
    private final ConversationReasoningGateway reasoningGateway;
    private final ObjectProvider<ConversationGenerationGateway> generationGateways;
    private final ConversationAnswerAssembler answerAssembler;
    private final ConversationEvidenceAuditService evidenceAudit;
    private final ConversationFallbackAnswerFactory fallbackFactory;
    private final ConversationHistoryService historyService;
    private final ConversationPolicyService conversationPolicy;

    @Override
    public ConversationAnswerDetails generate(
            @NonNull ConversationTurnExecutionContext context,
            @NonNull Consumer<ConversationProgressStage> progress
    ) {
        var history = historyService.recent(context);
        var conversationalReply = conversationPolicy.reply(context,
                conversationPolicy.route(context.userMessage(), history));
        if (conversationalReply.isPresent()) return conversationalReply.get();

        var evidence = evidenceCollector.collect(context, progress);

        evidenceAudit.record(context, evidence);

        // Do not ask the model to fill an evidence gap from its background knowledge.
        if (evidence.isEmpty()) {
            progress.accept(ConversationProgressStage.VALIDATING_ANSWER);
            return answerAssembler.assemble(context, evidence,
                    fallbackFactory.createMissingEvidence(context));
        }

        progress.accept(ConversationProgressStage.REASONING);

        var reasoning = reasoningGateway.reason(context, evidence);

        progress.accept(ConversationProgressStage.INTERPRETING);
        progress.accept(ConversationProgressStage.GENERATING_ANSWER);

        List<String> reasoningWarnings = reasoning.warningCodes();
        ConversationGenerationGateway gateway = generationGateways.getIfAvailable();
        ConversationGenerationResult generation;

        try {
            if (gateway == null)
                throw new ConversationGenerationUnavailableException("Conversation generation is disabled");

            generation = gateway.generate(
                    new ConversationGenerationRequest(
                            context.userMessage(),
                            context.language(),
                            history,
                            evidence,
                            reasoning
                    )
            );

            generation = generation.claims().isEmpty()
                    ? fallbackFactory.createAfterRejected(context, evidence, reasoningWarnings)
                    : mergeWarnings(generation, reasoningWarnings);
        } catch (ConversationGenerationRejectedException exception) {
            generation = fallbackFactory.createAfterRejected(context, evidence, reasoningWarnings);
        } catch (ConversationGenerationUnavailableException exception) {
            throw exception;
        }

        progress.accept(ConversationProgressStage.VALIDATING_ANSWER);

        try {
            return answerAssembler.assemble(context, evidence, generation);
        } catch (ConversationGenerationRejectedException exception) {
            return answerAssembler.assemble(
                    context,
                    evidence,
                    fallbackFactory.createAfterRejected(context, evidence, reasoningWarnings)
            );
        }
    }

    private ConversationAnswerDetails fallback(
            ConversationTurnExecutionContext context,
            ConversationEvidenceBundle evidence,
            List<String> warningCodes
    ) {
        return answerAssembler.assemble(
                context,
                evidence,
                fallbackFactory.create(
                        context,
                        evidence,
                        warningCodes
                )
        );
    }

    @Contract("_, _ -> new")
    private @NonNull ConversationGenerationResult mergeWarnings(
            @NonNull ConversationGenerationResult generation,
            @NonNull List<String> agentWarnings
    ) {
        return new ConversationGenerationResult(
                generation.answer(),
                generation.insufficientEvidence(),
                generation.claims(),
                generation.citedEvidenceIds(),
                Stream.concat(
                                agentWarnings.stream(),
                                generation.warningCodes().stream()
                        )
                        .distinct()
                        .toList()
        );
    }
}
