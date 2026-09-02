package fmi.ethnowear.application.service.conversation.turn;

import fmi.ethnowear.application.model.conversation.*;
import fmi.ethnowear.application.service.conversation.ConversationMapper;
import fmi.ethnowear.domain.model.conversation.ConversationProgressStage;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurn;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvent;
import fmi.ethnowear.persistence.jpa.repository.conversation.*;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ConversationTurnLifecycleService {

    private final ConversationRepository conversations;
    private final ConversationTurnRepository turns;
    private final ConversationTurnEventRepository turnEvents;
    private final ApplicationEventPublisher publisher;
    private final ConversationMapper mapper;
    private final Clock clock;

    @Transactional
    public Optional<ConversationTurnExecutionContext> start(@NonNull ConversationTurnQueuedEvent queued) {
        var conversation = conversations
                .findByPublicId(queued.conversationId())
                .orElse(null);

        if (conversation == null)
            return Optional.empty();

        var turn = turns.findForUpdate(
                conversation.getId(),
                queued.turnId()
        ).orElse(null);

        if (turn == null || turn.getStatus() != ConversationTurnStatus.QUEUED)
            return Optional.empty();

        turn.start(now());

        recordEvent(turn);

        return Optional.of(new ConversationTurnExecutionContext(
                conversation.getPublicId(),
                turn.getPublicId(),
                conversation.getLanguage(),
                turn.getUserMessage()
        ));
    }

    @Transactional
    public boolean advance(ConversationTurnQueuedEvent identity, ConversationProgressStage stage) {
        if (stage == null)
            throw new IllegalArgumentException("Conversation progress stage is required");

        var turn = lockedTurn(identity).orElse(null);

        if (turn == null || turn.getStatus() != ConversationTurnStatus.RUNNING)
            return false;

        if (turn.getStage() == stage)
            return false;

        turn.advance(stage);
        recordEvent(turn);
        return true;
    }

    @Transactional
    public boolean complete(ConversationTurnQueuedEvent identity, String validatedAnswerJson) {
        var turn = lockedTurn(identity).orElse(null);

        if (turn == null)
            return false;

        if (turn.getStatus() == ConversationTurnStatus.COMPLETED)
            return true;

        if (turn.getStatus() != ConversationTurnStatus.RUNNING)
            return false;

        turn.complete(validatedAnswerJson, now());
        recordEvent(turn);
        return true;
    }

    @Transactional
    public boolean fail(ConversationTurnQueuedEvent identity, String safeErrorCode) {
        var turn = lockedTurn(identity).orElse(null);

        if (turn == null)
            return false;

        if (turn.getStatus() == ConversationTurnStatus.FAILED)
            return safeErrorCode.equals(turn.getErrorCode());

        if (!turn.isActive())
            return false;

        turn.fail(safeErrorCode, now());
        recordEvent(turn);
        return true;
    }

    @Transactional
    public Optional<ConversationTurnStatus> cancel(@NonNull ConversationTurnQueuedEvent identity) {
        var turn = lockedTurn(identity).orElse(null);

        if (turn == null)
            return Optional.empty();

        if (turn.getStatus() == ConversationTurnStatus.CANCELLED)
            return Optional.of(ConversationTurnStatus.CANCELLED);

        if (!turn.isActive())
            return Optional.of(turn.getStatus());

        turn.cancel(now());
        recordEvent(turn);

        return Optional.of(ConversationTurnStatus.CANCELLED);
    }

    private Optional<ConversationTurn> lockedTurn(@NonNull ConversationTurnQueuedEvent identity) {
        return conversations.findByPublicId(identity.conversationId())
                .flatMap(conversation -> turns.findForUpdate(
                        conversation.getId(),
                        identity.turnId()
                ));
    }


    @Contract(" -> new")
    private @NonNull LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    private void recordEvent(ConversationTurn turn) {
        ConversationTurnEvent event = turnEvents.saveAndFlush(new ConversationTurnEvent(turn));

        publisher.publishEvent(new ConversationProgressCommittedEvent(mapper.toProgress(event)));
    }
}
