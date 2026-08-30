package fmi.ethnowear.application.model.event;

import java.time.Instant;

public record ManagementEvent(
        ResourceType resourceType,
        Action action,
        Long resourceId,
        Long documentId,
        Long pageId,
        String state,
        Instant occurredAt
) {
    public enum ResourceType {
        DOCUMENT,
        DOCUMENT_PAGE,
        PROCESSING_JOB,
        MEDIA_ASSET,
        INDEXING
    }

    public enum Action {
        CREATED,
        UPDATED,
        STATUS_CHANGED,
        DELETED
    }
}
