package fmi.ethnowear.application.service.event;

import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.model.worker.ClaimedWorkerJob;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@RequiredArgsConstructor
public class ManagementEventPublisher {

    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public void processingJob(
            @NonNull DocumentProcessingJob job,
            ManagementEvent.Action action
    ) {
        Document document = job.getDocument();
        DocumentPage page = job.getDocumentPage();

        publish(new ManagementEvent(
                ManagementEvent.ResourceType.PROCESSING_JOB,
                action,
                job.getId(),
                document == null ? null : document.getId(),
                page == null ? null : page.getId(),
                job.getStatus().name(),
                clock.instant()
        ));
    }

    public void processingJobClaimed(@NonNull ClaimedWorkerJob job) {
        publish(new ManagementEvent(
                ManagementEvent.ResourceType.PROCESSING_JOB,
                ManagementEvent.Action.STATUS_CHANGED,
                job.jobId(),
                job.documentId(),
                job.documentPageId(),
                "CLAIMED",
                clock.instant()
        ));
    }

    public void processingJobChanged() {
        publish(new ManagementEvent(
                ManagementEvent.ResourceType.PROCESSING_JOB,
                ManagementEvent.Action.STATUS_CHANGED,
                null,
                null,
                null,
                null,
                clock.instant()
        ));
    }

    public void document(@NonNull Document document, ManagementEvent.Action action) {
        publish(new ManagementEvent(
                ManagementEvent.ResourceType.DOCUMENT,
                action,
                document.getId(),
                document.getId(),
                null,
                document.getProcessingState() == null
                        ? null
                        : document.getProcessingState().name(),
                clock.instant()
        ));
    }

    public void documentDeleted(Long documentId) {
        publish(new ManagementEvent(
                ManagementEvent.ResourceType.DOCUMENT,
                ManagementEvent.Action.DELETED,
                documentId,
                documentId,
                null,
                null,
                clock.instant()
        ));
    }

    public void documentIndexingStateChanged(@NonNull Document document) {
        publish(new ManagementEvent(
                ManagementEvent.ResourceType.DOCUMENT,
                ManagementEvent.Action.STATUS_CHANGED,
                document.getId(),
                document.getId(),
                null,
                document.getIndexingState().name(),
                clock.instant()
        ));
    }

    public void page(@NonNull DocumentPage page, ManagementEvent.Action action) {
        publish(new ManagementEvent(
                ManagementEvent.ResourceType.DOCUMENT_PAGE,
                action,
                page.getId(),
                page.getDocument().getId(),
                page.getId(),
                page.getProcessingState() == null
                        ? null
                        : page.getProcessingState().name(),
                clock.instant()
        ));
    }

    public void pageIndexingStateChanged(@NonNull DocumentPage page) {
        publish(new ManagementEvent(
                ManagementEvent.ResourceType.DOCUMENT_PAGE,
                ManagementEvent.Action.STATUS_CHANGED,
                page.getId(),
                page.getDocument().getId(),
                page.getId(),
                page.getIndexingState().name(),
                clock.instant()
        ));
    }

    public void media(@NonNull MediaAsset media, ManagementEvent.Action action) {
        publish(new ManagementEvent(
                ManagementEvent.ResourceType.MEDIA_ASSET,
                action,
                media.getId(),
                null,
                null,
                null,
                clock.instant()
        ));
    }

    private void publish(ManagementEvent event) {
        publisher.publishEvent(event);
    }
}
