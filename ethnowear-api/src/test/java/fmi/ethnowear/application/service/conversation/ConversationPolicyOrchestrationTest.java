package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.dto.conversation.ConversationAnswerDetails;
import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.model.conversation.*;
import fmi.ethnowear.application.port.conversation.*;
import fmi.ethnowear.application.service.conversation.evidence.ConversationEvidenceAuditService;
import fmi.ethnowear.application.service.conversation.generation.*;
import fmi.ethnowear.application.service.conversation.orchestration.*;
import fmi.ethnowear.application.service.conversation.turn.ConversationHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConversationPolicyOrchestrationTest {
    private final ConversationEvidenceCollector collector = mock(ConversationEvidenceCollector.class);
    private final ConversationReasoningGateway reasoner = mock(ConversationReasoningGateway.class);
    private final ObjectProvider<ConversationGenerationGateway> gateways = mock(ObjectProvider.class);
    private final ConversationAnswerAssembler assembler = mock(ConversationAnswerAssembler.class);
    private final ConversationEvidenceAuditService audit = mock(ConversationEvidenceAuditService.class);
    private final ConversationFallbackAnswerFactory fallback = mock(ConversationFallbackAnswerFactory.class);
    private final ConversationHistoryService history = mock(ConversationHistoryService.class);
    private final ConversationPolicyService policy = mock(ConversationPolicyService.class);
    private final DefaultConversationAnswerOrchestrator orchestrator = new DefaultConversationAnswerOrchestrator(
            collector, reasoner, gateways, assembler, audit, fallback, history, policy);
    private final ConversationTurnExecutionContext context = new ConversationTurnExecutionContext(UUID.randomUUID(), UUID.randomUUID(), "bg", "Въпрос");

    @Test
    void conversationalReplyDoesNotInvokeEvidenceAgentsOrGeneration() {
        var answer = new ConversationAnswerDetails(context.conversationId(), context.turnId(), "Здравей!", false,
                List.of(), List.of(), List.of(), List.of());
        when(policy.reply(eq(context), any())).thenReturn(Optional.of(answer));
        assertThat(orchestrator.generate(context, stage -> {})).isSameAs(answer);
        verifyNoInteractions(collector, reasoner, gateways, assembler, audit);
    }

    @Test
    void missingEvidenceNeverAsksModelToAnswerFromMemory() {
        when(policy.reply(eq(context), any())).thenReturn(Optional.empty());
        when(collector.collect(eq(context), any())).thenReturn(new ConversationEvidenceBundle(List.of(), List.of(), List.of(), List.of(), List.of()));
        orchestrator.generate(context, stage -> {});
        verifyNoInteractions(reasoner, gateways);
        verify(fallback).createMissingEvidence(context);
    }

    @Test
    void unclaimedModelProseCannotEscapeThroughInsufficientEvidenceFlag() {
        when(policy.reply(eq(context), any())).thenReturn(Optional.empty());
        var passage = new GroundedPassageDetails(12L, "Източник за шевици", "bg", null, 1L, "Книга", List.of(), 0.8, null, null, null, true);
        var evidence = new ConversationEvidenceBundle(List.of(passage), List.of(), List.of(), List.of(), List.of());
        when(collector.collect(eq(context), any())).thenReturn(evidence);
        when(reasoner.reason(context, evidence)).thenReturn(new ConversationReasoningResult(List.of(), List.of()));
        var gateway = mock(ConversationGenerationGateway.class);
        when(gateways.getIfAvailable()).thenReturn(gateway);
        when(gateway.generate(any())).thenReturn(new ConversationGenerationResult("Unsupported rocket facts", true, List.of(), List.of(), List.of()));
        var safe = new ConversationGenerationResult("Ограничени сведения", true, List.of(), List.of(), List.of());
        when(fallback.createAfterRejected(eq(context), eq(evidence), any())).thenReturn(safe);
        orchestrator.generate(context, stage -> {});
        verify(assembler).assemble(context, evidence, safe);
    }
}
