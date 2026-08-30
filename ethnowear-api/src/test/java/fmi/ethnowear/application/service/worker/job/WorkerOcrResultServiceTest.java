package fmi.ethnowear.application.service.worker.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.ocr.WorkerOcrResultCommand;
import fmi.ethnowear.application.dto.worker.ocr.WorkerOcrResultDetails;
import fmi.ethnowear.application.dto.worker.figure.WorkerFigureCandidateCommand;
import fmi.ethnowear.application.exception.WorkerOcrResultConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.service.document.ocr.OcrResultImportValidator;
import fmi.ethnowear.application.service.document.figure.FigureBoundsValidator;
import fmi.ethnowear.config.FigureExtractionProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigureCandidate;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureCandidateRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.util.unit.DataSize;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class WorkerOcrResultServiceTest {

    @Test
    void acceptsOnePendingResultAndMakesIdenticalRetryIdempotent() {
        Fixture fixture = fixture();
        WorkerOcrResultCommand command = command("Разпознат текст");

        WorkerOcrResultDetails accepted = fixture.service().accept(
                11L,
                credentials(),
                command
        );
        WorkerOcrResultDetails repeated = fixture.service().accept(
                11L,
                credentials(),
                command
        );

        assertFalse(accepted.existing());
        assertTrue(repeated.existing());
        assertEquals(21L, accepted.pageId());
        assertEquals(31L, accepted.inputMediaId());
        assertFalse(fixture.result().get().isCurrent());
    }

    @Test
    void rejectsConflictingRetryAndOversizedText() {
        Fixture fixture = fixture();
        fixture.service().accept(11L, credentials(), command("first"));

        assertThrows(
                WorkerOcrResultConflictException.class,
                () -> fixture.service().accept(
                        11L,
                        credentials(),
                        command("different")
                )
        );
        assertThrows(
                WorkerPayloadTooLargeException.class,
                () -> fixture.service().accept(
                        11L,
                        credentials(),
                        command("x".repeat(
                                properties().maximumOcrTextCharacters() + 1
                        ))
                )
        );
    }

    @Test
    void storesBoundedFigureCandidatesAndRejectsEscapingBounds() {
        Fixture fixture = fixture();
        WorkerOcrResultCommand command = command(
                "Разпознат текст",
                List.of(
                        candidate(1, "Фиг. 1"),
                        candidate(2, null)
                )
        );

        fixture.service().accept(11L, credentials(), command);

        assertEquals(2, fixture.candidates().get().size());
        assertEquals(
                fmi.ethnowear.domain.model.document.figure.FigureExtractionState.PENDING,
                fixture.result().get().getFigureExtractionState()
        );

        WorkerFigureCandidateCommand invalid = new WorkerFigureCandidateCommand(
                1,
                new BigDecimal("0.8000000"),
                new BigDecimal("0.1000000"),
                new BigDecimal("0.3000000"),
                new BigDecimal("0.2000000"),
                null,
                null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> fixture().service().accept(
                        11L,
                        credentials(),
                        command("text", List.of(invalid))
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
        input.setSizeBytes(1024L);

        DocumentPageMedia pageMedia = new DocumentPageMedia();
        EntityTestUtils.setId(pageMedia, 41L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(input);
        pageMedia.setPreferredOcrInput(true);

        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        job.setJobType(JobType.OCR);
        job.setDocumentPage(page);
        job.setInputMediaAsset(input);

        DocumentPageMediaRepository mediaRepository = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals(
                            "findByDocumentPage_IdAndPreferredOcrInputTrue"
                    ))
                        return Optional.of(pageMedia);

                    throw new AssertionError(
                            "Unexpected media repository call: " + method.getName()
                    );
                }
        );

        AtomicReference<DocumentPageOcrResult> stored = new AtomicReference<>();
        DocumentPageOcrResultRepository resultRepository = proxy(
                DocumentPageOcrResultRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByProcessingJob_Id" -> Optional.ofNullable(stored.get());
                    case "saveAndFlush" -> {
                        DocumentPageOcrResult result =
                                (DocumentPageOcrResult) arguments[0];
                        EntityTestUtils.setId(result, 51L);
                        stored.set(result);
                        yield result;
                    }
                    default -> throw new AssertionError(
                            "Unexpected OCR repository call: " + method.getName()
                    );
                }
        );

        OcrResultImportValidator validator = new OcrResultImportValidator(
                Validation.buildDefaultValidatorFactory().getValidator(),
                new ObjectMapper()
        );
        AtomicReference<List<DocumentPageFigureCandidate>> storedCandidates =
                new AtomicReference<>(List.of());
        DocumentPageFigureCandidateRepository candidates = proxy(
                DocumentPageFigureCandidateRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "saveAllAndFlush" -> {
                        @SuppressWarnings("unchecked")
                        List<DocumentPageFigureCandidate> saved =
                                (List<DocumentPageFigureCandidate>) arguments[0];
                        storedCandidates.set(List.copyOf(saved));
                        yield saved;
                    }
                    case "findByDocumentPageOcrResult_IdOrderByCandidateOrdinalAsc" ->
                            storedCandidates.get();
                    default -> throw new AssertionError(
                            "Unexpected figure-candidate repository call: " + method.getName()
                    );
                }
        );

        return new Fixture(
                new WorkerOcrResultService(
                        loader(job),
                        mediaRepository,
                        resultRepository,
                        candidates,
                        validator,
                        properties(),
                        new FigureExtractionProperties(
                                100,
                                2000,
                                100,
                                DataSize.ofMegabytes(25)
                        ),
                        new FigureBoundsValidator()
                ),
                stored,
                storedCandidates
        );
    }

    private WorkerOcrResultCommand command(String rawText) {
        return command(rawText, List.of());
    }

    private WorkerOcrResultCommand command(
            String rawText,
            List<WorkerFigureCandidateCommand> candidates
    ) {
        return new WorkerOcrResultCommand(
                rawText,
                "tesseract",
                "5.5",
                "bul",
                new BigDecimal("0.9123"),
                "{\"psm\":6}",
                "{\"words\":[]}",
                candidates
        );
    }

    private WorkerFigureCandidateCommand candidate(int ordinal, String caption) {
        return new WorkerFigureCandidateCommand(
                ordinal,
                new BigDecimal("0.1000000"),
                new BigDecimal("0.1000000"),
                new BigDecimal("0.2000000"),
                new BigDecimal("0.2000000"),
                caption,
                new BigDecimal("0.9000")
        );
    }

    private record Fixture(
            WorkerOcrResultService service,
            AtomicReference<DocumentPageOcrResult> result,
            AtomicReference<List<DocumentPageFigureCandidate>> candidates
    ) {
    }
}
