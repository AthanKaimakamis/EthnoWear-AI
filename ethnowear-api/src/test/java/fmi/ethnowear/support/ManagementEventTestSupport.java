package fmi.ethnowear.support;

import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.model.worker.ClaimedWorkerJob;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;

import java.time.Clock;

public final class ManagementEventTestSupport {

    private ManagementEventTestSupport() {
    }

    public static ManagementEventPublisher events() {
        return new ManagementEventPublisher(
                event -> {
                },
                Clock.systemUTC()
        ) {
            @Override
            public void processingJob(
                    DocumentProcessingJob job,
                    ManagementEvent.Action action
            ) {
            }

            @Override
            public void processingJobClaimed(ClaimedWorkerJob job) {
            }

            @Override
            public void processingJobChanged() {
            }

            @Override
            public void document(Document document, ManagementEvent.Action action) {
            }

            @Override
            public void page(DocumentPage page, ManagementEvent.Action action) {
            }

            @Override
            public void media(MediaAsset media, ManagementEvent.Action action) {
            }
        };
    }
}
