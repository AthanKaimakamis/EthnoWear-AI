package fmi.ethnowear.infrastructure.sse;

import fmi.ethnowear.application.dto.conversation.ConversationProgressDetails;
import fmi.ethnowear.application.model.conversation.ConversationProgressCommittedEvent;
import fmi.ethnowear.application.model.conversation.ConversationProgressStreamTarget;
import fmi.ethnowear.application.service.conversation.ConversationMapper;
import fmi.ethnowear.persistence.jpa.repository.conversation.ConversationTurnEventRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

@Component
@RequiredArgsConstructor
public class ConversationEventBroadcaster {

    private static final int MAXIMUM_REPLAY_EVENTS = 100;
    private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();

    private final ConversationTurnEventRepository events;
    private final ConversationMapper mapper;

    private final Map<UUID, Map<UUID, Subscriber>> subscribers = new HashMap<>();

    @Transactional(readOnly = true)
    public synchronized SseEmitter subscribe(ConversationProgressStreamTarget target, long afterEventId) {
        if (afterEventId < 0)
            throw new IllegalArgumentException("Conversation event cursor cannot be negative");

        UUID subscriptionId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter(TIMEOUT);

        Subscriber subscriber = new Subscriber(
                subscriptionId,
                target.turnId(),
                emitter,
                afterEventId
        );

        subscribers
                .computeIfAbsent(target.turnId(), ignored -> new HashMap<>())
                .put(subscriptionId, subscriber);

        emitter.onCompletion(() -> remove(subscriber));
        emitter.onTimeout(() -> remove(subscriber));
        emitter.onError(ignored -> remove(subscriber));

        events.findByTurn_IdAndEventIdGreaterThanOrderByEventIdAsc(
                        target.internalTurnId(),
                        afterEventId,
                        PageRequest.of(0, MAXIMUM_REPLAY_EVENTS)
                )
                .stream()
                .map(mapper::toProgress)
                .forEach(event -> send(subscriber, event));

        if (target.terminal()
                && subscriber.cursor() >= target.lastEventId()) {
            emitter.complete();
            remove(subscriber);
        }

        return emitter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public synchronized void publish(ConversationProgressCommittedEvent committed) {
        ConversationProgressDetails event = committed.details();

        Map<UUID, Subscriber> turnSubscribers = subscribers.get(event.turnId());

        if (turnSubscribers == null)
            return;

        List.copyOf(turnSubscribers.values())
                .forEach(subscriber -> send(subscriber, event));
    }

    @Scheduled(fixedDelayString = "${ethnowear.conversation.sse-heartbeat-interval:20s}")
    public synchronized void heartbeat() {
        List<Subscriber> active = subscribers.values()
                .stream()
                .flatMap(values -> values.values().stream())
                .toList();

        active.forEach(subscriber -> {
            try {
                subscriber.emitter().send(
                        SseEmitter.event().comment("keepalive")
                );
            } catch (IOException ex) {
                subscriber.emitter().completeWithError(ex);
                remove(subscriber);
            }
        });
    }

    private void send(@NonNull Subscriber subscriber, @NonNull ConversationProgressDetails event) {
        if (event.eventId() <= subscriber.cursor())
            return;

        try {
            subscriber.emitter().send(
                    SseEmitter.event()
                            .id(Long.toString(event.eventId()))
                            .name("conversation-progress")
                            .data(event)
            );

            subscriber.advance(event.eventId());

            if (event.status().isTerminal()) {
                subscriber.emitter().complete();
                remove(subscriber);
            }
        } catch (IOException ex) {
            subscriber.emitter().completeWithError(ex);
            remove(subscriber);
        }
    }

    private synchronized void remove(@NonNull Subscriber subscriber) {
        Map<UUID, Subscriber> turnSubscribers = subscribers.get(subscriber.turnId());

        if (turnSubscribers == null)
            return;

        turnSubscribers.remove(subscriber.subscriptionId());

        if (turnSubscribers.isEmpty())
            subscribers.remove(subscriber.turnId());
    }

    private static final class Subscriber {

        private final UUID subscriptionId;
        private final UUID turnId;
        private final SseEmitter emitter;
        private long cursor;

        private Subscriber(
                UUID subscriptionId,
                UUID turnId,
                SseEmitter emitter,
                long cursor
        ) {
            this.subscriptionId = subscriptionId;
            this.turnId = turnId;
            this.emitter = emitter;
            this.cursor = cursor;
        }

        private UUID subscriptionId() {
            return subscriptionId;
        }

        private UUID turnId() {
            return turnId;
        }

        private SseEmitter emitter() {
            return emitter;
        }

        private long cursor() {
            return cursor;
        }

        private void advance(long eventId) {
            cursor = eventId;
        }
    }
}
