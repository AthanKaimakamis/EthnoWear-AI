package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.exception.UnprocessableDocumentEvidenceException;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDelivery;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDeliveryService;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerJobInputServiceTest {

    @Test
    void returnsControlledDeliveryForTheClaimedJobsPdf() {
        DocumentProcessingJob job = jobWithInput(pdf(31L, "application/pdf", 1024L));

        MediaDelivery result = service(job).findInput(11L, credentials());

        assertInstanceOf(MediaDelivery.Redirect.class, result);
        assertEquals(URI.create("https://media.invalid/content"), ((MediaDelivery.Redirect) result).location());
    }

    @Test
    void rejectsAnotherJobAndInvalidPdfEvidence() {
        DocumentProcessingJob job = jobWithInput(pdf(31L, "image/png", 1024L));

        assertThrows(ResourceNotFoundException.class, () -> service(job).findInput(12L, credentials()));
        assertThrows(
                UnprocessableDocumentEvidenceException.class,
                () -> service(job).findInput(11L, credentials())
        );
    }

    private WorkerJobInputService service(DocumentProcessingJob job) {
        MediaDeliveryService deliveryService = new MediaDeliveryService(null, null) {
            @Override
            public MediaDelivery findById(Long id) {
                assertEquals(31L, id);
                return new MediaDelivery.Redirect(URI.create("https://media.invalid/content"));
            }
        };

        return new WorkerJobInputService(deliveryService, properties(), loader(job));
    }

    private DocumentProcessingJob jobWithInput(MediaAsset input) {
        Document document = document(7L);
        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        job.setInputMediaAsset(input);
        return job;
    }

    private MediaAsset pdf(long id, String mimeType, long size) {
        MediaAsset asset = new MediaAsset();
        EntityTestUtils.setId(asset, id);
        asset.setMediaType(MediaType.PDF);
        asset.setMimeType(mimeType);
        asset.setSizeBytes(size);
        return asset;
    }
}
