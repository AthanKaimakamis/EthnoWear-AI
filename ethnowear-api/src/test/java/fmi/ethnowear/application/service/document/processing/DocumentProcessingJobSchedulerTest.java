package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.exception.VisionJobAlreadyActiveException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.Clock;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.time.LocalDateTime;

import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class DocumentProcessingJobSchedulerTest {

    @Test
    void queuesOneActiveFigureJobForCurrentOcrEvidence() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentPageOcrResult result = entity(new DocumentPageOcrResult(), 21L);
        result.setDocumentPage(page);
        result.setCurrent(true);
        DocumentProcessingJob previous = entity(new DocumentProcessingJob(), 31L);
        previous.setJobType(JobType.EXTRACT_PAGE_FIGURES);
        previous.setStatus(JobStatus.SUCCEEDED);
        previous.setDocumentPage(page);
        AtomicReference<DocumentProcessingJob> saved = new AtomicReference<>();

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByActiveJobKey" -> Optional.empty();
                    case "findFirstByDocumentPage_IdAndJobTypeOrderByCreatedAtDescIdDesc" ->
                            Optional.of(previous);
                    case "saveAndFlush" -> {
                        DocumentProcessingJob job = (DocumentProcessingJob) arguments[0];
                        EntityTestUtils.setId(job, 32L);
                        saved.set(job);
                        yield job;
                    }
                    default -> throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );

        DocumentProcessingJob job = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        ).queueFigureExtraction(page, media, result);

        assertEquals(
                "EXTRACT_PAGE_FIGURES:PAGE:8:OCR_RESULT:21",
                job.getActiveJobKey()
        );
        assertSame(previous, job.getPreviousJob());
        assertSame(job, saved.get());
    }

    @Test
    void rejectsDuplicateActiveFigureJob() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentPageOcrResult result = entity(new DocumentPageOcrResult(), 21L);
        result.setDocumentPage(page);
        result.setCurrent(true);
        DocumentProcessingJob active = entity(new DocumentProcessingJob(), 31L);

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("findByActiveJobKey"))
                        return Optional.of(active);
                    throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );

        assertThrows(
                ActiveDocumentJobExistsException.class,
                () -> new DocumentProcessingJobScheduler(
                        repository,
                        new DocumentProcessingJobKeyFactory(),
                        fmi.ethnowear.support.ManagementEventTestSupport.events()
                ).queueFigureExtraction(page, media, result)
        );
    }

    @Test
    void createsFreshSequentialVisionAttemptAfterTerminalAttempt() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentPageMedia pageMedia = entity(new DocumentPageMedia(), 12L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(media);
        DocumentPageOcrResult ocrResult = entity(new DocumentPageOcrResult(), 21L);
        ocrResult.setDocumentPage(page);
        ocrResult.setDocumentPageMedia(pageMedia);
        ocrResult.setCurrent(true);
        DocumentPageQualityAssessment assessment = entity(
                new DocumentPageQualityAssessment(),
                31L
        );
        assessment.setDocumentPage(page);
        assessment.setDocumentPageMedia(pageMedia);
        assessment.setDocumentPageOcrResult(ocrResult);
        assessment.setCurrent(true);
        DocumentProcessingJob previous = entity(new DocumentProcessingJob(), 41L);
        previous.setJobType(JobType.VISION_OCR_ASSESSMENT);
        previous.setStatus(JobStatus.SUCCEEDED);
        previous.setDocumentPage(page);
        AtomicReference<ManagementEvent.Action> action = new AtomicReference<>();

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByActiveJobKey" -> Optional.empty();
                    case "findFirstByDocumentPage_IdAndJobTypeOrderByCreatedAtDescIdDesc" ->
                            Optional.of(previous);
                    case "saveAndFlush" -> {
                        DocumentProcessingJob job = (DocumentProcessingJob) arguments[0];
                        EntityTestUtils.setId(job, 42L);
                        yield job;
                    }
                    default -> throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );
        ManagementEventPublisher events = new ManagementEventPublisher(
                ignored -> {
                },
                Clock.systemUTC()
        ) {
            @Override
            public void processingJob(
                    DocumentProcessingJob job,
                    ManagementEvent.Action eventAction
            ) {
                action.set(eventAction);
            }
        };

        DocumentProcessingJob created = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                events
        ).queueVisionOcrAssessment(page, media, ocrResult, assessment);

        assertEquals(42L, created.getId());
        assertSame(previous, created.getPreviousJob());
        assertEquals(
                "VISION_OCR_ASSESSMENT:PAGE:8:OCR_RESULT:21",
                created.getActiveJobKey()
        );
        assertTrue(created.getJobKey().startsWith(
                "VISION_OCR_ASSESSMENT:OCR_RESULT:21:QUALITY_ASSESSMENT:31:JOB:"
        ));
        assertEquals(ManagementEvent.Action.CREATED, action.get());
    }

    @Test
    void rejectsConcurrentVisionRequestWithSafeActiveJobDetails() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentPageMedia pageMedia = entity(new DocumentPageMedia(), 12L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(media);
        DocumentPageOcrResult ocrResult = entity(new DocumentPageOcrResult(), 21L);
        ocrResult.setDocumentPage(page);
        ocrResult.setDocumentPageMedia(pageMedia);
        ocrResult.setCurrent(true);
        DocumentPageQualityAssessment assessment = entity(
                new DocumentPageQualityAssessment(),
                31L
        );
        assessment.setDocumentPage(page);
        assessment.setDocumentPageOcrResult(ocrResult);
        assessment.setCurrent(true);
        DocumentProcessingJob active = entity(new DocumentProcessingJob(), 51L);
        active.setJobType(JobType.VISION_OCR_ASSESSMENT);
        active.setStatus(JobStatus.RUNNING);
        active.setDocumentPage(page);

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    return switch (method.getName()) {
                        case "findFirstByDocumentPage_IdAndJobTypeOrderByCreatedAtDescIdDesc" ->
                                Optional.empty();
                        case "findByActiveJobKey" -> Optional.of(active);
                        default -> throw new AssertionError(
                                "Unexpected job call: " + method.getName()
                        );
                    };
                }
        );

        VisionJobAlreadyActiveException error = assertThrows(
                VisionJobAlreadyActiveException.class,
                () -> new DocumentProcessingJobScheduler(
                        repository,
                        new DocumentProcessingJobKeyFactory(),
                        fmi.ethnowear.support.ManagementEventTestSupport.events()
                ).queueVisionOcrAssessment(page, media, ocrResult, assessment)
        );

        assertEquals(51L, error.getActiveJobId());
        assertEquals(JobStatus.RUNNING, error.getActiveJobStatus());
        assertEquals(8L, error.getDocumentPageId());
        assertEquals(21L, error.getOcrResultId());
    }

    @Test
    void rejectsVisionRequestForStaleOcrResult() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentPageMedia pageMedia = entity(new DocumentPageMedia(), 12L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(media);
        DocumentPageOcrResult stale = entity(new DocumentPageOcrResult(), 21L);
        stale.setDocumentPage(page);
        stale.setDocumentPageMedia(pageMedia);
        stale.setCurrent(false);
        DocumentPageQualityAssessment assessment = entity(
                new DocumentPageQualityAssessment(),
                31L
        );
        assessment.setDocumentPage(page);
        assessment.setDocumentPageOcrResult(stale);
        assessment.setCurrent(true);

        assertThrows(
                InvalidDocumentProcessingRequestException.class,
                () -> new DocumentProcessingJobScheduler(
                        proxy(DocumentProcessingJobRepository.class, (ignored, method, arguments) -> {
                            throw new AssertionError("Repository must not be called");
                        }),
                        new DocumentProcessingJobKeyFactory(),
                        fmi.ethnowear.support.ManagementEventTestSupport.events()
                ).queueVisionOcrAssessment(page, media, stale, assessment)
        );
    }

    @Test
    void createsLinkedReplacementOcrJobAndPublishesCreated() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentProcessingJob previous = entity(new DocumentProcessingJob(), 13L);
        previous.setJobType(JobType.OCR);
        previous.setStatus(JobStatus.FAILED);
        previous.setDocumentPage(page);
        AtomicReference<ManagementEvent.Action> action = new AtomicReference<>();

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByActiveJobKey" -> Optional.empty();
                    case "findFirstByDocumentPage_IdAndJobTypeOrderByCreatedAtDescIdDesc" ->
                            Optional.of(previous);
                    case "saveAndFlush" -> {
                        DocumentProcessingJob job = (DocumentProcessingJob) arguments[0];
                        EntityTestUtils.setId(job, 14L);
                        yield job;
                    }
                    default -> throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );
        ManagementEventPublisher events = new ManagementEventPublisher(
                ignored -> {
                },
                Clock.systemUTC()
        ) {
            @Override
            public void processingJob(
                    DocumentProcessingJob job,
                    ManagementEvent.Action eventAction
            ) {
                action.set(eventAction);
            }
        };

        DocumentProcessingJob result = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                events
        ).createNewOcrJob(page, media);

        assertEquals(14L, result.getId());
        assertSame(previous, result.getPreviousJob());
        assertEquals("OCR:PAGE:8", result.getActiveJobKey());
        assertTrue(result.getJobKey().startsWith("OCR:PAGE:8:JOB:"));
        assertEquals(ManagementEvent.Action.CREATED, action.get());
    }

    @Test
    void linksOcrReplacementToTheExplicitSelectedJob() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentProcessingJob selected = entity(new DocumentProcessingJob(), 13L);
        selected.setJobType(JobType.OCR);
        selected.setStatus(JobStatus.FAILED);
        selected.setDocument(document);
        selected.setDocumentPage(page);
        selected.setInputMediaAsset(media);

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByActiveJobKey" -> Optional.empty();
                    case "saveAndFlush" -> arguments[0];
                    default -> throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );

        DocumentProcessingJob replacement = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        ).createReplacementOcr(selected);

        assertSame(selected, replacement.getPreviousJob());
        assertEquals("OCR:PAGE:8", replacement.getActiveJobKey());
        assertNotEquals(selected.getJobKey(), replacement.getJobKey());
    }

    @Test
    void doesNotResetSucceededStableJob() {
        Document document = entity(new Document(), 7L);
        DocumentProcessingJob succeeded = entity(new DocumentProcessingJob(), 13L);
        succeeded.setJobType(JobType.CHUNK_GENERATION);
        succeeded.setStatus(JobStatus.SUCCEEDED);
        succeeded.assignJobKey("CHUNK_GENERATION:DOCUMENT:7");

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("findByActiveJobKey"))
                        return Optional.empty();

                    if (method.getName().equals("findByJobKeyForUpdate"))
                        return Optional.of(succeeded);

                    throw new AssertionError(
                            "Succeeded job must not be saved again: " + method.getName()
                    );
                }
        );

        DocumentProcessingJob result = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        ).queueChunkGeneration(document, "0".repeat(64));

        assertSame(succeeded, result);
        assertEquals(JobStatus.SUCCEEDED, result.getStatus());
    }

    @Test
    void rejectsReplacementWhenOcrJobIsActive() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("findByActiveJobKey"))
                        return Optional.of(new DocumentProcessingJob());

                    throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );

        assertThrows(
                ActiveDocumentJobExistsException.class,
                () -> new DocumentProcessingJobScheduler(
                        repository,
                        new DocumentProcessingJobKeyFactory(),
                        fmi.ethnowear.support.ManagementEventTestSupport.events()
                ).createNewOcrJob(page, media)
        );
    }

    @Test
    void createsNewOcrJobForEveryAttempt() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentProcessingJob existing = entity(new DocumentProcessingJob(), 14L);
        existing.setJobType(JobType.OCR);
        existing.setStatus(JobStatus.FAILED);
        existing.setDocumentPage(page);
        existing.assignJobKey("OCR:PAGE:8:JOB:replacement");

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByActiveJobKey" -> Optional.empty();
                    case "findFirstByDocumentPage_IdAndJobTypeOrderByCreatedAtDescIdDesc" ->
                            Optional.of(existing);
                    case "saveAndFlush" -> {
                        DocumentProcessingJob job = (DocumentProcessingJob) arguments[0];
                        EntityTestUtils.setId(job, 15L);
                        yield job;
                    }
                    default -> throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );

        DocumentProcessingJob result = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        ).queueOcr(page, media);

        assertNotSame(existing, result);
        assertEquals(15L, result.getId());
        assertSame(existing, result.getPreviousJob());
        assertTrue(result.getJobKey().startsWith("OCR:PAGE:8:JOB:"));
        assertNotEquals(existing.getJobKey(), result.getJobKey());
        assertEquals("OCR:PAGE:8", result.getActiveJobKey());
    }

    @Test
    void keysQualityAssessmentByOcrResult() {
        Document document = entity(new Document(), 7L);
        DocumentPage page = entity(new DocumentPage(), 8L);
        page.setDocument(document);
        MediaAsset media = entity(new MediaAsset(), 11L);
        var result = entity(
                new fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult(),
                30154L
        );

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByJobKeyForUpdate" -> Optional.empty();
                    case "saveAndFlush" -> arguments[0];
                    default -> throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );

        DocumentProcessingJob job = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        ).queueOcrQualityAssessment(page, media, result);

        assertEquals(
                "OCR_QUALITY_ASSESSMENT:OCR_RESULT:30154",
                job.getJobKey()
        );
        assertEquals(job.getJobKey(), job.getActiveJobKey());
    }

    @Test
    void queuesPageExtractionWithServerManagedActiveKey() {
        Document document = entity(new Document(), 7L);
        MediaAsset media = entity(new MediaAsset(), 11L);
        AtomicReference<DocumentProcessingJob> savedJob = new AtomicReference<>();

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByJobKeyForUpdate" -> Optional.empty();
                    case "saveAndFlush" -> {
                        DocumentProcessingJob job = (DocumentProcessingJob) arguments[0];
                        EntityTestUtils.setId(job, 13L);
                        savedJob.set(job);
                        yield job;
                    }
                    default -> throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );

        DocumentProcessingJob result = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        ).queuePageExtraction(document, media);

        assertSame(savedJob.get(), result);
        assertEquals(13L, result.getId());
        assertEquals(JobType.PAGE_EXTRACTION, result.getJobType());
        assertEquals(JobStatus.QUEUED, result.getStatus());
        assertEquals("PAGE_EXTRACTION:DOCUMENT:7", result.getActiveJobKey());
        assertEquals("PAGE_EXTRACTION:DOCUMENT:7", result.getJobKey());
        assertSame(document, result.getDocument());
        assertSame(media, result.getInputMediaAsset());
        assertNull(result.getDocumentPage());
        assertEquals(0, result.getAttemptCount());
        assertEquals(3, result.getMaxAttempts());
        assertNotNull(result.getAvailableAt());
        assertNotNull(result.getCorrelationId());
    }

    @Test
    void rejectsKnownDuplicateActiveJobBeforeInsert() {
        Document document = entity(new Document(), 7L);
        MediaAsset media = entity(new MediaAsset(), 11L);

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("findByJobKeyForUpdate")) {
                        DocumentProcessingJob existing = new DocumentProcessingJob();
                        existing.assignActiveJobKey("PAGE_EXTRACTION:DOCUMENT:7");
                        return Optional.of(existing);
                    }

                    throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );

        var scheduler = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        );

        assertThrows(
                ActiveDocumentJobExistsException.class,
                () -> scheduler.queuePageExtraction(document, media)
        );
    }

    @Test
    void translatesDatabaseUniqueIndexRaceToDomainException() {
        Document document = entity(new Document(), 7L);
        MediaAsset media = entity(new MediaAsset(), 11L);
        SQLException sqlException = new SQLException(
                "Violation of unique index UQ_DocumentProcessingJobs_ActiveJobKey",
                "23000",
                2601
        );

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByJobKeyForUpdate" -> Optional.empty();
                    case "saveAndFlush" -> throw new DataIntegrityViolationException(
                            "Duplicate active job",
                            sqlException
                    );
                    default -> throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );

        var scheduler = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        );

        assertThrows(
                ActiveDocumentJobExistsException.class,
                () -> scheduler.queuePageExtraction(document, media)
        );
    }

    @Test
    void leavesSucceededStableJobUnchanged() {
        Document document = entity(new Document(), 7L);
        MediaAsset media = entity(new MediaAsset(), 11L);
        DocumentProcessingJob existing = entity(
                new DocumentProcessingJob(),
                13L
        );
        existing.setJobType(JobType.PAGE_EXTRACTION);
        existing.setStatus(JobStatus.SUCCEEDED);
        existing.setAttemptCount(3);
        existing.setMaxAttempts(3);
        existing.assignJobKey("PAGE_EXTRACTION:DOCUMENT:7");

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByJobKeyForUpdate" -> Optional.of(existing);
                    default -> throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );

        DocumentProcessingJob result = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        ).queuePageExtraction(document, media);

        assertSame(existing, result);
        assertEquals(13L, result.getId());
        assertEquals(JobStatus.SUCCEEDED, result.getStatus());
        assertEquals(3, result.getAttemptCount());
    }

    @Test
    void queuesDelayedMediaCleanupWithUniqueHistoryKey() {
        Document document = entity(new Document(), 7L);
        LocalDateTime retentionUntil = LocalDateTime.of(2026, 9, 23, 12, 0);
        DocumentProcessingJob previous = entity(new DocumentProcessingJob(), 18L);
        AtomicReference<DocumentProcessingJob> saved = new AtomicReference<>();

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByActiveJobKey" -> Optional.empty();
                    case "findFirstByDocument_IdAndJobTypeOrderByCreatedAtDescIdDesc" ->
                            Optional.of(previous);
                    case "saveAndFlush" -> {
                        DocumentProcessingJob job =
                                (DocumentProcessingJob) arguments[0];
                        EntityTestUtils.setId(job, 19L);
                        saved.set(job);
                        yield job;
                    }
                    default -> throw new AssertionError(
                            "Unexpected job call: " + method.getName()
                    );
                }
        );

        DocumentProcessingJob result = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory(),
                fmi.ethnowear.support.ManagementEventTestSupport.events()
        ).queueMediaCleanup(
                document,
                retentionUntil,
                MediaRetentionPolicy.KEEP_ORIGINAL_ONLY
        );

        assertSame(saved.get(), result);
        assertEquals(JobType.MEDIA_CLEANUP, result.getJobType());
        assertEquals(retentionUntil, result.getAvailableAt());
        assertEquals("MEDIA_CLEANUP:DOCUMENT:7", result.getActiveJobKey());
        assertTrue(result.getJobKey().startsWith(
                "MEDIA_CLEANUP:DOCUMENT:7:JOB:"
        ));
        assertSame(previous, result.getPreviousJob());
        assertEquals(
                "{\"policy\":\"KEEP_ORIGINAL_ONLY\"}",
                result.getParametersJson()
        );
    }

    private <T extends fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
