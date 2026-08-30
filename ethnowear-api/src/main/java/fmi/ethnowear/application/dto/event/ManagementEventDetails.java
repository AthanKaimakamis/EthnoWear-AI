package fmi.ethnowear.application.dto.event;

import fmi.ethnowear.application.model.event.ManagementEvent;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;

import java.time.Instant;


public record ManagementEventDetails(
        long eventId,
        ManagementEvent.ResourceType resourceType,
        ManagementEvent.Action action,
        Long resourceId,
        Long documentId,
        Long pageId,
        String state,
        Instant occurredAt
) {
    @Contract("_, _ -> new")
    public static @NonNull ManagementEventDetails from(
            long eventId,
            @NonNull ManagementEvent event
    ) {
        return new ManagementEventDetails(
                eventId,
                event.resourceType(),
                event.action(),
                event.resourceId(),
                event.documentId(),
                event.pageId(),
                event.state(),
                event.occurredAt()
        );
    }
}
