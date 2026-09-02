package fmi.ethnowear.application.service.conversation;

import fmi.ethnowear.application.dto.conversation.ConversationCreateCommand;
import fmi.ethnowear.application.dto.conversation.ConversationDetails;
import fmi.ethnowear.application.exception.ConversationException;
import fmi.ethnowear.application.model.conversation.ConversationOwner;
import fmi.ethnowear.application.dto.conversation.*;
import fmi.ethnowear.application.model.conversation.ConversationProgressStreamTarget;
import fmi.ethnowear.application.model.conversation.ConversationTurnQueuedEvent;
import fmi.ethnowear.application.model.conversation.ConversationProgressCommittedEvent;
import fmi.ethnowear.application.service.conversation.policy.ConversationRateLimiter;
import fmi.ethnowear.application.service.conversation.turn.ConversationTurnLifecycleService;
import fmi.ethnowear.domain.model.conversation.ConversationTurnStatus;
import fmi.ethnowear.persistence.jpa.entity.conversation.Conversation;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurn;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationGuestSessionRepository;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationRepository;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationTurnEventRepository;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationTurnEvidenceRepository;
import fmi.ethnowear.persistence.jpa.repository.publicuser.PublicUserRepository;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationTurnRepository;
import fmi.ethnowear.persistence.jpa.entity.conversation.ConversationTurnEvent;
import fmi.ethnowear.util.PageableUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConversationService {

    private static final int MAXIMUM_PAGE_SIZE = 50;

    private final ConversationRepository conversations;
    private final PublicUserRepository publicUsers;
    private final ConversationGuestSessionRepository guestSessions;
    private final ConversationTurnRepository turns;
    private final ApplicationEventPublisher events;
    private final ConversationTurnEventRepository turnEvents;
    private final ConversationTurnEvidenceRepository turnEvidence;
    private final ConversationTurnLifecycleService lifecycle;
    private final ConversationRateLimiter rateLimiter;
    private final ConversationMapper mapper;


    @Transactional
    public ConversationDetails create(ConversationOwner owner, ConversationCreateCommand command) {
        if (owner == null)
            throw new IllegalArgumentException("Conversation owner is required");

        if (command == null)
            throw new IllegalArgumentException("Conversation command is required");

        Optional<Conversation> existing = findByRequest(owner, command.clientRequestId());

        if (existing.isPresent())
            return mapper.toDetails(existing.get());

        var publicUser = owner.isPublicUser()
                ? publicUsers.getReferenceById(owner.publicUserId())
                : null;

        var guestSession = owner.isPublicUser()
                ? null
                : guestSessions.getReferenceById(owner.guestSessionId());

        try {
            return mapper.toDetails(conversations.saveAndFlush(
                    new Conversation(
                            publicUser,
                            guestSession,
                            command.clientRequestId(),
                            command.language()
                    )
            ));
        } catch (DataIntegrityViolationException ex) {
            return findByRequest(owner, command.clientRequestId())
                    .map(mapper::toDetails)
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional
    public ConversationTurnAcceptedDetails submit(ConversationOwner owner, UUID conversationId, ConversationMessageCommand command) {
        requireOwner(owner);

        if (command == null)
            throw new IllegalArgumentException("Conversation message is required");

        var owned = findOwned(owner, conversationId)
                .orElseThrow(this::conversationNotFound);

        var conversation = conversations.findForUpdate(owned.getId())
                .orElseThrow(this::conversationNotFound);

        var existing = turns.findByConversation_IdAndClientRequestId(
                conversation.getId(),
                command.clientRequestId()
        );

        if (existing.isPresent()) {
            var turn = existing.get();
            String requestHash = ContentHashUtils.sha256(command.text());

            if (!turn.getRequestHash().equals(requestHash))
                throw new ConversationException(
                        HttpStatus.CONFLICT,
                        "CONVERSATION_REQUEST_CONFLICT",
                        "Client request ID was already used for another message"
                );

            if (turn.getStatus() == ConversationTurnStatus.QUEUED)
                events.publishEvent(new ConversationTurnQueuedEvent(
                        conversation.getPublicId(),
                        turn.getPublicId()
                ));

            return mapper.toAccepted(turn);
        }

        rateLimiter.acquire(owner);

        if (turns.existsByConversation_IdAndActiveTrue(conversation.getId()))
            throw new ConversationException(
                    HttpStatus.CONFLICT,
                    "CONVERSATION_TURN_ACTIVE",
                    "The conversation already has an active request"
            );

        long nextSequence = turns
                .findFirstByConversation_IdOrderByTurnSequenceDesc(conversation.getId())
                .map(previous -> Math.addExact(previous.getTurnSequence(), 1))
                .orElse(1L);

        var turn = turns.saveAndFlush(new ConversationTurn(
                conversation,
                command.clientRequestId(),
                nextSequence,
                command.text()
        ));

        recordProgress(turn);

        var accepted = mapper.toAccepted(turn);

        events.publishEvent(new ConversationTurnQueuedEvent(
                accepted.conversationId(),
                accepted.turnId()
        ));

        return accepted;
    }

    @Transactional
    public ConversationTurnDetails cancel(ConversationOwner owner,UUID conversationId,UUID turnId) {
        requireOwner(owner);

        Conversation conversation = findOwned(owner, conversationId)
                .orElseThrow(this::conversationNotFound);

        if (turns.findByConversation_IdAndPublicId(
                conversation.getId(),
                turnId
        ).isEmpty())
            throw turnNotFound();

        ConversationTurnStatus status = lifecycle.cancel(
                        new ConversationTurnQueuedEvent(
                                conversation.getPublicId(),
                                turnId
                        )
                )
                .orElseThrow(this::turnNotFound);

        if (status != ConversationTurnStatus.CANCELLED)
            throw new ConversationException(
                    HttpStatus.CONFLICT,
                    "CONVERSATION_TURN_NOT_CANCELLABLE",
                    "Completed or failed conversation turns cannot be cancelled"
            );

        return turns.findByConversation_IdAndPublicId(
                        conversation.getId(),
                        turnId
                )
                .map(mapper::toTurnDetails)
                .orElseThrow(this::turnNotFound);
    }

    public Page<ConversationDetails> list(ConversationOwner owner, Pageable pageable) {
        requireOwner(owner);

        Pageable bounded = PageableUtils.boundedUnsorted(pageable, MAXIMUM_PAGE_SIZE, "Conversation");

        Pageable ordered = PageRequest.of(
                bounded.getPageNumber(),
                bounded.getPageSize(),
                Sort.by(Sort.Direction.DESC, "updatedAt")
        );

        Page<Conversation> result = owner.isPublicUser()
                ? conversations.findByPublicUser_Id(owner.publicUserId(), ordered)
                : conversations.findByGuestSession_Id(owner.guestSessionId(), ordered);

        return result.map(mapper::toDetails);
    }

    public ConversationDetails get(ConversationOwner owner, UUID conversationId) {
        requireOwner(owner);

        return findOwned(owner, conversationId)
                .map(mapper::toDetails)
                .orElseThrow(this::conversationNotFound);
    }

    @Transactional
    public ConversationDetails rename(
            ConversationOwner owner,
            UUID conversationId,
            ConversationRenameCommand command
    ) {
        requireOwner(owner);

        if (command == null)
            throw new IllegalArgumentException("Conversation rename command is required");

        Conversation conversation = findOwnedForUpdate(owner, conversationId);
        conversation.setTitle(command.title());
        return mapper.toDetails(conversations.saveAndFlush(conversation));
    }

    @Transactional
    public void delete(ConversationOwner owner, UUID conversationId) {
        requireOwner(owner);

        Conversation conversation = findOwnedForUpdate(owner, conversationId);

        if (turns.existsByConversation_IdAndActiveTrue(conversation.getId()))
            throw new ConversationException(
                    HttpStatus.CONFLICT,
                    "CONVERSATION_TURN_ACTIVE",
                    "A conversation with an active request cannot be deleted"
            );

        turnEvents.deleteByConversationId(conversation.getId());
        turnEvidence.deleteByConversationId(conversation.getId());
        turns.deleteByConversation_Id(conversation.getId());
        conversations.delete(conversation);
        conversations.flush();
    }

    public Page<ConversationTurnDetails> listTurns(ConversationOwner owner, UUID conversationId, Pageable pageable) {
        requireOwner(owner);

        Conversation conversation = findOwned(owner, conversationId)
                .orElseThrow(this::conversationNotFound);

        Pageable bounded = PageableUtils.boundedUnsorted(
                pageable,
                MAXIMUM_PAGE_SIZE,
                "Conversation turns"
        );

        Pageable ordered = PageRequest.of(
                bounded.getPageNumber(),
                bounded.getPageSize()
        );

        return turns.findByConversation_IdOrderByTurnSequenceAsc(
                conversation.getId(),
                ordered
        ).map(mapper::toTurnDetails);
    }

    public ConversationTurnDetails getTurn(ConversationOwner owner, UUID conversationId, UUID turnId) {
        requireOwner(owner);

        Conversation conversation = findOwned(owner, conversationId)
                .orElseThrow(this::conversationNotFound);

        return turns.findByConversation_IdAndPublicId(
                        conversation.getId(),
                        turnId
                )
                .map(mapper::toTurnDetails)
                .orElseThrow(this::turnNotFound);
    }

    public List<ConversationProgressDetails> progress(ConversationOwner owner, UUID conversationId, UUID turnId, long afterEventId) {
        if (afterEventId < 0)
            throw new IllegalArgumentException("Conversation event cursor cannot be negative");

        Conversation conversation = findOwned(owner, conversationId)
                .orElseThrow(this::conversationNotFound);

        ConversationTurn turn = turns.findByConversation_IdAndPublicId(
                        conversation.getId(),
                        turnId
                )
                .orElseThrow(this::turnNotFound);

        return turnEvents
                .findByTurn_IdAndEventIdGreaterThanOrderByEventIdAsc(
                        turn.getId(),
                        afterEventId,
                        PageRequest.of(0, 100)
                )
                .stream()
                .map(mapper::toProgress)
                .toList();
    }

    public ConversationProgressStreamTarget progressTarget(ConversationOwner owner, UUID conversationId, UUID turnId) {
        requireOwner(owner);

        Conversation conversation = findOwned(owner, conversationId)
                .orElseThrow(this::conversationNotFound);

        ConversationTurn turn = turns
                .findByConversation_IdAndPublicId(
                        conversation.getId(),
                        turnId
                )
                .orElseThrow(this::turnNotFound);

        return new ConversationProgressStreamTarget(
                turn.getId(),
                turn.getPublicId(),
                turn.getStatus().isTerminal(),
                turn.getLastEventId()
        );
    }

    @Contract(" -> new")
    private @NonNull ConversationException turnNotFound() {
        return new ConversationException(
                HttpStatus.NOT_FOUND,
                "CONVERSATION_TURN_NOT_FOUND",
                "Conversation turn does not exist"
        );
    }

    private Optional<Conversation> findByRequest(@NonNull ConversationOwner owner, UUID clientRequestId) {
        if (owner.isPublicUser())
            return conversations.findByClientRequestIdAndPublicUser_Id(
                    clientRequestId,
                    owner.publicUserId()
            );

        return conversations.findByClientRequestIdAndGuestSession_Id(
                clientRequestId,
                owner.guestSessionId()
        );
    }

    private Optional<Conversation> findOwned(@NonNull ConversationOwner owner, UUID conversationId) {
        if (owner.isPublicUser())
            return conversations.findByPublicIdAndPublicUser_Id(
                    conversationId,
                    owner.publicUserId()
            );
        return conversations.findByPublicIdAndGuestSession_Id(
                conversationId,
                owner.guestSessionId()
        );
    }

    private Conversation findOwnedForUpdate(ConversationOwner owner, UUID conversationId) {
        Conversation owned = findOwned(owner, conversationId)
                .orElseThrow(this::conversationNotFound);

        return conversations.findForUpdate(owned.getId())
                .orElseThrow(this::conversationNotFound);
    }

    private void requireOwner(ConversationOwner owner) {
        if (owner == null)
            throw new IllegalArgumentException("Conversation owner is required");
    }

    private void recordProgress(ConversationTurn turn) {
        ConversationTurnEvent event = turnEvents.saveAndFlush(new ConversationTurnEvent(turn));

        events.publishEvent(new ConversationProgressCommittedEvent(mapper.toProgress(event)));
    }

    @Contract(" -> new")
    private @NonNull ConversationException conversationNotFound() {
        return new ConversationException(
                HttpStatus.NOT_FOUND,
                "CONVERSATION_NOT_FOUND",
                "Conversation does not exist"
        );
    }
}
