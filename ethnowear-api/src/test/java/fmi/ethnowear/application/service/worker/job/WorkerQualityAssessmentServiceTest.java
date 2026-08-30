package fmi.ethnowear.application.service.worker.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.quality.*;
import fmi.ethnowear.application.exception.WorkerQualityAssessmentConflictException;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualitySignalRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerQualityAssessmentServiceTest {

    @Test
    void returnsBoundedContextAndAcceptsIdempotentAssessment() {
        Fixture fixture = fixture();

        WorkerQualityAssessmentContextDetails context = fixture.service().context(
                11L,
                credentials()
        );
        WorkerQualityAssessmentDetails accepted = fixture.service().accept(
                11L,
                credentials(),
                command(WorkerQualityStatus.PASS, "Readable")
        );
        WorkerQualityAssessmentDetails repeated = fixture.service().accept(
                11L,
                credentials(),
                command(WorkerQualityStatus.PASS, "Readable")
        );

        assertEquals(51L, context.ocrResultId());
        assertEquals(21L, context.pageId());
        assertEquals(31L, context.inputMediaId());
        assertEquals("Разпознат текст", context.rawOcrText());
        assertEquals(1200, context.imageWidth());
        assertFalse(accepted.existing());
        assertTrue(repeated.existing());
        assertFalse(fixture.assessment().get().isCurrent());
    }

    @Test
    void rejectsConflictingRetry() {
        Fixture fixture = fixture();
        fixture.service().accept(
                11L,
                credentials(),
                command(WorkerQualityStatus.WARNING, "Review punctuation")
        );

        assertThrows(
                WorkerQualityAssessmentConflictException.class,
                () -> fixture.service().accept(
                        11L,
                        credentials(),
                        command(WorkerQualityStatus.FAIL, "Unreadable")
                )
        );
    }

    private Fixture fixture() {
        Document document = document(7L);
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 21L);
        page.setDocument(document);

        MediaAsset input = new MediaAsset();
        EntityTestUtils.setId(input, 31L);
        input.setMediaType(MediaType.IMAGE);
        input.setMimeType("image/png");
        input.setSizeBytes(2048L);
        input.setWidth(1200);
        input.setHeight(1800);

        DocumentPageMedia pageMedia = new DocumentPageMedia();
        EntityTestUtils.setId(pageMedia, 41L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(input);
        pageMedia.setWidth(1200);
        pageMedia.setHeight(1800);
        pageMedia.setDpi(300);

        DocumentPageOcrResult ocrResult = new DocumentPageOcrResult();
        EntityTestUtils.setId(ocrResult, 51L);
        ocrResult.setDocumentPage(page);
        ocrResult.setDocumentPageMedia(pageMedia);
        ocrResult.setRawText("Разпознат текст");
        ocrResult.setOcrLanguage("bul");
        ocrResult.setOcrConfidence(new BigDecimal("0.9123"));
        ocrResult.setStructuredOutputJson("{\"words\":[]}");
        ocrResult.setCurrent(true);

        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        job.setJobType(JobType.OCR_QUALITY_ASSESSMENT);
        job.setDocumentPage(page);
        job.setInputMediaAsset(input);
        job.assignJobKey("OCR_QUALITY_ASSESSMENT:OCR_RESULT:51");

        DocumentPageOcrResultRepository results = proxy(
                DocumentPageOcrResultRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("findById"))
                        return Optional.of(ocrResult);

                    throw new AssertionError("Unexpected OCR repository call: " + method.getName());
                }
        );

        AtomicReference<DocumentPageQualityAssessment> stored = new AtomicReference<>();
        DocumentPageQualityAssessmentRepository assessments = proxy(
                DocumentPageQualityAssessmentRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByProcessingJob_Id" -> Optional.ofNullable(stored.get());
                    case "saveAndFlush" -> {
                        DocumentPageQualityAssessment assessment =
                                (DocumentPageQualityAssessment) arguments[0];
                        EntityTestUtils.setId(assessment, 61L);
                        stored.set(assessment);
                        yield assessment;
                    }
                    default -> throw new AssertionError(
                            "Unexpected assessment repository call: " + method.getName()
                    );
                }
        );

        List<DocumentPageQualitySignal> storedSignals = new ArrayList<>();
        DocumentPageQualitySignalRepository signals = proxy(
                DocumentPageQualitySignalRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "saveAllAndFlush" -> {
                        storedSignals.clear();
                        for (Object signal : (Iterable<?>) arguments[0])
                            storedSignals.add((DocumentPageQualitySignal) signal);
                        yield arguments[0];
                    }
                    case "findByAssessment_IdOrderBySignalOrdinalAscIdAsc" ->
                            List.copyOf(storedSignals);
                    default -> throw new AssertionError(
                            "Unexpected signal repository call: " + method.getName()
                    );
                }
        );

        return new Fixture(
                new WorkerQualityAssessmentService(
                        loader(job),
                        results,
                        assessments,
                        signals,
                        properties(),
                        new ObjectMapper(),
                        new fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory()
                ),
                stored
        );
    }

    private WorkerQualityAssessmentCommand command(
            WorkerQualityStatus status,
            String message
    ) {
        return new WorkerQualityAssessmentCommand(
                "deterministic-quality",
                "1.0",
                "quality-v1",
                new BigDecimal("0.8500"),
                status,
                "  Safe summary  ",
                "Needs human verification",
                List.of(new WorkerQualitySignalCommand(
                        "character-confidence",
                        new BigDecimal("0.850000"),
                        null,
                        QualitySignalSeverity.WARNING,
                        new BigDecimal("0.5000"),
                        message
                ))
        );
    }

    private record Fixture(
            WorkerQualityAssessmentService service,
            AtomicReference<DocumentPageQualityAssessment> assessment
    ) {
    }
}
