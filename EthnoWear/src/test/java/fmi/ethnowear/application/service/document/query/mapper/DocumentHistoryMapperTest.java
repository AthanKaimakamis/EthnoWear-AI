package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageProvenanceEvent;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageReview;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentHistoryMapperTest {

    private final DocumentHistoryMapper mapper = new DocumentHistoryMapper();

    @Test
    void exposesSafeErrorWithoutInternalJobPayloads() {
        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 9L);
        job.setJobType(JobType.OCR);
        job.setStatus(JobStatus.FAILED);
        job.setSafeErrorMessage("OCR could not read the page");
        job.setErrorDetailsJson("{\"stackTrace\":\"internal\"}");
        job.setParametersJson("{\"secret\":true}");

        var result = mapper.toDetails(job);

        assertEquals(9L, result.id());
        assertEquals("OCR could not read the page", result.safeErrorMessage());
    }

    @Test
    void mapsEveryPageHistoryProjection() {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 5L);

        DocumentPageOcrResult ocr = new DocumentPageOcrResult();
        EntityTestUtils.setId(ocr, 10L);
        ocr.setDocumentPage(page);
        ocr.setRawText("raw text");

        DocumentPageReview review = new DocumentPageReview();
        EntityTestUtils.setId(review, 20L);
        review.setDocumentPage(page);
        review.setReviewer("curator");

        DocumentPageProvenanceEvent provenance =
                new DocumentPageProvenanceEvent();
        EntityTestUtils.setId(provenance, 30L);
        provenance.setDocumentPage(page);
        provenance.setReviewedBy("curator");
        provenance.setReason("source verified");

        assertEquals(5L, mapper.toDetails(ocr).documentPageId());
        assertEquals("raw text", mapper.toDetails(ocr).rawText());
        assertEquals(5L, mapper.toDetails(review).documentPageId());
        assertEquals("curator", mapper.toDetails(review).reviewer());
        assertEquals(5L, mapper.toDetails(provenance).documentPageId());
        assertEquals("source verified", mapper.toDetails(provenance).reason());
    }
}
