package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class DocumentProcessingJobSchedulerTest {

    @Test
    void queuesPageExtractionWithServerManagedActiveKey() {
        Document document = entity(new Document(), 7L);
        MediaAsset media = entity(new MediaAsset(), 11L);
        AtomicReference<DocumentProcessingJob> savedJob = new AtomicReference<>();

        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByActiveJobKey" -> Optional.empty();
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
                new DocumentProcessingJobKeyFactory()
        ).queuePageExtraction(document, media);

        assertSame(savedJob.get(), result);
        assertEquals(13L, result.getId());
        assertEquals(JobType.PAGE_EXTRACTION, result.getJobType());
        assertEquals(JobStatus.QUEUED, result.getStatus());
        assertEquals("PAGE_EXTRACTION:DOCUMENT:7", result.getActiveJobKey());
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
                    if (method.getName().equals("findByActiveJobKey"))
                        return Optional.of(new DocumentProcessingJob());

                    throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );

        var scheduler = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory()
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
                    case "findByActiveJobKey" -> Optional.empty();
                    case "saveAndFlush" -> throw new DataIntegrityViolationException(
                            "Duplicate active job",
                            sqlException
                    );
                    default -> throw new AssertionError("Unexpected job call: " + method.getName());
                }
        );

        var scheduler = new DocumentProcessingJobScheduler(
                repository,
                new DocumentProcessingJobKeyFactory()
        );

        assertThrows(
                ActiveDocumentJobExistsException.class,
                () -> scheduler.queuePageExtraction(document, media)
        );
    }

    private <T extends fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
