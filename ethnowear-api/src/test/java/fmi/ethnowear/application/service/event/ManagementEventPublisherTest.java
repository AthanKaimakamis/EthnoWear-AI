package fmi.ethnowear.application.service.event;

import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ManagementEventPublisherTest {

    @Test
    void publishesSafeProcessingJobIdentityAndState() {
        AtomicReference<Object> published = new AtomicReference<>();
        ApplicationEventPublisher springEvents = published::set;
        ManagementEventPublisher publisher = new ManagementEventPublisher(
                springEvents,
                Clock.fixed(Instant.parse("2026-08-23T09:00:00Z"), ZoneOffset.UTC)
        );
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 11L);
        page.setDocument(document);
        DocumentProcessingJob job = entity(new DocumentProcessingJob(), 13L);
        job.setDocument(document);
        job.setDocumentPage(page);
        job.setStatus(JobStatus.RUNNING);

        publisher.processingJob(
                job,
                ManagementEvent.Action.STATUS_CHANGED
        );

        ManagementEvent event = (ManagementEvent) published.get();
        assertEquals(ManagementEvent.ResourceType.PROCESSING_JOB, event.resourceType());
        assertEquals(13L, event.resourceId());
        assertEquals(7L, event.documentId());
        assertEquals(11L, event.pageId());
        assertEquals("RUNNING", event.state());
        assertEquals(Instant.parse("2026-08-23T09:00:00Z"), event.occurredAt());
    }

    private <T extends fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity> T entity(
            T entity,
            Long id
    ) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
