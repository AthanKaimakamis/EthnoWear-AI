package fmi.ethnowear.infrastructure.sse;

import fmi.ethnowear.application.dto.event.ManagementEventDetails;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.config.ManagementEventProperties;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class ManagementEventBroadcaster {

    private static final long RECONNECT_DELAY_MILLISECONDS = 3000;

    private final ManagementEventProperties properties;

    private final Object monitor = new Object();
    private final AtomicLong sequence = new AtomicLong();
    private final Map<UUID, SseEmitter> subscribers = new HashMap<>();
    private final Deque<ManagementEventDetails> history = new ArrayDeque<>();

    public SseEmitter subscribe(Long lastEventId) {
        UUID subscriberId = UUID.randomUUID();
        SseEmitter emitter = createEmitter();

        emitter.onCompletion(() -> remove(subscriberId));
        emitter.onTimeout(() -> remove(subscriberId));
        emitter.onError(error -> remove(subscriberId));

        synchronized (monitor) {
            try {
                emitter.send(SseEmitter.event()
                        .comment("connected")
                        .reconnectTime(RECONNECT_DELAY_MILLISECONDS));

                replay(emitter, lastEventId);
                subscribers.put(subscriberId, emitter);
            } catch (IOException | IllegalStateException ex) {
                emitter.completeWithError(ex);
            }
        }

        return emitter;
    }

    SseEmitter createEmitter() {
        return new SseEmitter(properties.connectionTimeout().toMillis());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void afterCommit(ManagementEvent event) {
        ManagementEventDetails details = ManagementEventDetails.from(
                sequence.incrementAndGet(),
                event
        );

        synchronized (monitor) {
            history.addLast(details);

            while (history.size() > properties.replayCapacity())
                history.removeFirst();

            List<UUID> failedSubscribers = new ArrayList<>();

            subscribers.forEach((id, emitter) -> {
                try {
                    send(emitter, details);
                } catch (IOException | IllegalStateException ex) {
                    failedSubscribers.add(id);
                }
            });

            failedSubscribers.forEach(this::remove);
        }
    }

    @Scheduled(fixedDelayString = "${ethnowear.management-events.heartbeat-interval:25s}")
    public void heartbeat() {
        synchronized (monitor) {
            List<UUID> failedSubscribers = new ArrayList<>();

            subscribers.forEach((id, emitter) -> {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException | IllegalStateException ex) {
                    failedSubscribers.add(id);
                }
            });

            failedSubscribers.forEach(this::remove);
        }
    }

    private void replay(SseEmitter emitter, Long lastEventId) throws IOException {
        if (lastEventId == null)
            return;

        long latestId = sequence.get();

        if (history.isEmpty()) {
            if (lastEventId != latestId)
                sendResyncRequired(emitter, latestId);

            return;
        }

        long oldestId = history.getFirst().eventId();

        if (lastEventId > latestId || lastEventId < oldestId - 1) {
            sendResyncRequired(emitter, latestId);
            return;
        }

        for (ManagementEventDetails event : history) {
            if (event.eventId() > lastEventId)
                send(emitter, event);
        }
    }

    private void send(@NonNull SseEmitter emitter, @NonNull ManagementEventDetails details) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(details.eventId()))
                .name("management-event")
                .data(details));
    }

    private void sendResyncRequired(@NonNull SseEmitter emitter, long latestId) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(latestId))
                .name("resync-required")
                .data(Map.of(
                        "latestEventId", latestId,
                        "message", "Refresh authoritative API data"
                ))
        );
    }

    private void remove(UUID subscriberId) {
        synchronized (monitor) {
            subscribers.remove(subscriberId);
        }
    }
}
