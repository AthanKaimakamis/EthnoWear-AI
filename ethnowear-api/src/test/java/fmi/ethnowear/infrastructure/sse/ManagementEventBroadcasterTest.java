package fmi.ethnowear.infrastructure.sse;

import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.config.ManagementEventProperties;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagementEventBroadcasterTest {

    @Test
    void sendsCommittedEventsAndReplaysAvailableHistory() {
        TestBroadcaster broadcaster = broadcaster(2);
        RecordingEmitter first = (RecordingEmitter) broadcaster.subscribe(null);

        broadcaster.afterCommit(event(41L, "RUNNING"));

        assertEquals(2, first.sendCount());

        RecordingEmitter replayed = (RecordingEmitter) broadcaster.subscribe(0L);
        assertEquals(2, replayed.sendCount());
    }

    @Test
    void sendsResyncInstructionWhenCursorIsOlderThanHistory() {
        TestBroadcaster broadcaster = broadcaster(1);
        broadcaster.afterCommit(event(41L, "RUNNING"));
        broadcaster.afterCommit(event(42L, "SUCCEEDED"));

        RecordingEmitter emitter = (RecordingEmitter) broadcaster.subscribe(0L);

        assertEquals(2, emitter.sendCount());
    }

    private TestBroadcaster broadcaster(int replayCapacity) {
        return new TestBroadcaster(
                new ManagementEventProperties(
                        Duration.ofMinutes(5),
                        Duration.ofSeconds(25),
                        replayCapacity
                )
        );
    }

    private ManagementEvent event(Long id, String state) {
        return new ManagementEvent(
                ManagementEvent.ResourceType.PROCESSING_JOB,
                ManagementEvent.Action.STATUS_CHANGED,
                id,
                7L,
                null,
                state,
                Instant.parse("2026-08-23T09:00:00Z")
        );
    }

    private static final class TestBroadcaster extends ManagementEventBroadcaster {

        private TestBroadcaster(ManagementEventProperties properties) {
            super(properties);
        }

        @Override
        SseEmitter createEmitter() {
            return new RecordingEmitter();
        }
    }

    private static final class RecordingEmitter extends SseEmitter {

        private final List<SseEventBuilder> events = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            events.add(builder);
        }

        private int sendCount() {
            return events.size();
        }
    }
}
