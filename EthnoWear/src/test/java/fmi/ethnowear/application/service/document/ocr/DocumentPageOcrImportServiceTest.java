package fmi.ethnowear.application.service.document.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.document.command.ocr.OcrResultImportCommand;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.*;

class DocumentPageOcrImportServiceTest {

    @Test
    void preservesPreviousResultAndUpdatesCurrentSnapshotAtomically() {
        DocumentPage page = entity(new DocumentPage(), 11L);
        page.setCorrectedText("Curator text must remain unchanged");
        DocumentPageMedia pageMedia = entity(new DocumentPageMedia(), 21L);
        pageMedia.setDocumentPage(page);
        DocumentPageOcrResult previous = entity(new DocumentPageOcrResult(), 31L);
        previous.setDocumentPage(page);
        previous.setRawText("first OCR attempt");
        previous.setCurrent(true);
        List<DocumentPageOcrResult> flushOrder = new ArrayList<>();
        AtomicReference<DocumentPage> savedPage = new AtomicReference<>();

        DocumentPageOcrResultRepository results = proxy(
                DocumentPageOcrResultRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByDocumentPage_IdAndCurrentTrue" -> Optional.of(previous);
                    case "saveAndFlush" -> {
                        DocumentPageOcrResult result = (DocumentPageOcrResult) arguments[0];
                        if(result.getId() == null)
                            EntityTestUtils.setId(result, 32L);
                        flushOrder.add(result);
                        yield result;
                    }
                    default -> throw new AssertionError("Unexpected OCR-result call: " + method.getName());
                }
        );

        DocumentPageOcrImportService service = new DocumentPageOcrImportService(
                pageRepository(page, savedPage),
                pageMediaRepository(pageMedia),
                results,
                rejecting(DocumentProcessingJobRepository.class),
                validator(),
                new DocumentHistoryMapper()
        );

        var details = service.importResult(
                11L,
                command(null)
        );

        assertEquals(List.of(previous, flushOrder.get(1)), flushOrder);
        assertFalse(previous.isCurrent());
        assertTrue(flushOrder.get(1).isCurrent());
        assertEquals(32L, details.id());
        assertEquals(11L, details.documentPageId());
        assertEquals(21L, details.documentPageMediaId());
        assertEquals("second OCR attempt", details.rawText());
        assertEquals("second OCR attempt", savedPage.get().getRawOcrText());
        assertEquals("Curator text must remain unchanged", savedPage.get().getCorrectedText());
        assertEquals(ProcessingState.COMPLETED, savedPage.get().getProcessingState());
        assertEquals(ReviewState.REVIEW_REQUIRED, savedPage.get().getReviewState());
        assertEquals(
                TranscriptionApprovalState.PENDING,
                savedPage.get().getTranscriptionApprovalState()
        );
        assertEquals(IndexingState.NOT_ELIGIBLE, savedPage.get().getIndexingState());
    }

    @Test
    void rejectsProcessingJobThatIsNotAnOcrJob() {
        DocumentPage page = entity(new DocumentPage(), 11L);
        DocumentPageMedia pageMedia = entity(new DocumentPageMedia(), 21L);
        DocumentProcessingJob job = entity(new DocumentProcessingJob(), 41L);
        job.setJobType(JobType.PAGE_EXTRACTION);
        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByIdAndDocumentPage_Id"))
                        return Optional.of(job);
                    throw new AssertionError("Unexpected processing-job call: " + method.getName());
                }
        );

        DocumentPageOcrImportService service = new DocumentPageOcrImportService(
                pageRepository(page, new AtomicReference<>()),
                pageMediaRepository(pageMedia),
                rejecting(DocumentPageOcrResultRepository.class),
                jobs,
                validator(),
                new DocumentHistoryMapper()
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.importResult(11L, command(41L))
        );

        assertEquals("The processing job must be an OCR job", exception.getMessage());
    }

    @Test
    void rejectsInvalidProcessorJson() {
        OcrResultImportCommand command = new OcrResultImportCommand(
                21L,
                null,
                "raw text",
                "MANUAL_IMPORT",
                null,
                "bg",
                null,
                "not-json",
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator().validate(command)
        );
    }

    @Test
    void rejectsNonObjectProcessorParameters() {
        OcrResultImportCommand command = new OcrResultImportCommand(
                21L,
                null,
                "raw text",
                "MANUAL_IMPORT",
                null,
                "bg",
                null,
                "[]",
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> validator().validate(command)
        );
    }

    private DocumentPageRepository pageRepository(
            DocumentPage page,
            AtomicReference<DocumentPage> saved
    ) {
        return proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByIdForUpdate" -> Optional.of(page);
                    case "save" -> {
                        saved.set((DocumentPage) arguments[0]);
                        yield arguments[0];
                    }
                    default -> throw new AssertionError("Unexpected page call: " + method.getName());
                }
        );
    }

    private DocumentPageMediaRepository pageMediaRepository(DocumentPageMedia pageMedia) {
        return proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findByIdAndDocumentPage_Id"))
                        return Optional.of(pageMedia);
                    throw new AssertionError("Unexpected page-media call: " + method.getName());
                }
        );
    }

    private OcrResultImportValidator validator() {
        var validatorFactory = Validation.buildDefaultValidatorFactory();

        return new OcrResultImportValidator(
                validatorFactory.getValidator(),
                new ObjectMapper()
        );
    }

    private OcrResultImportCommand command(Long processingJobId) {
        return new OcrResultImportCommand(
                21L,
                processingJobId,
                "second OCR attempt",
                " MANUAL_IMPORT ",
                "1",
                "bg",
                new BigDecimal("0.7500"),
                "{\"source\":\"manual\"}",
                "{\"blocks\":[]}"
        );
    }

    private <T extends AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
