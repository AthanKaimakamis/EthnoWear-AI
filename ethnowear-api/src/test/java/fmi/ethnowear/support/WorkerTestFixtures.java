package fmi.ethnowear.support;

import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.worker.security.WorkerClaimValidator;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.config.WorkerIndexingProperties;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.springframework.util.unit.DataSize;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

public final class WorkerTestFixtures {

    public static final Instant NOW = Instant.parse("2026-08-21T08:00:00Z");
    public static final String WORKER_ID = "page-extractor-1";
    public static final String CLAIM_TOKEN = "current-claim-token";

    private WorkerTestFixtures() {
    }

    public static WorkerClaimCredentials credentials() {
        return new WorkerClaimCredentials(WORKER_ID, CLAIM_TOKEN);
    }

    public static WorkerApiProperties properties() {
        return new WorkerApiProperties(
                true,
                "test-worker-token-with-at-least-32-bytes",
                Duration.ofSeconds(30),
                Duration.ofSeconds(120),
                Duration.ofMinutes(5),
                Duration.ofSeconds(30),
                Duration.ofSeconds(30),
                Duration.ofMinutes(30),
                DataSize.ofMegabytes(250),
                DataSize.ofMegabytes(25),
                2000,
                300,
                20000,
                20000,
                100000000,
                2_000_000,
                DataSize.ofMegabytes(10),
                DataSize.ofMegabytes(16),
                DataSize.ofMegabytes(1),
                100,
                100,
                500,
                1000,
                1000,
                1000,
                DataSize.ofMegabytes(10),
                2_000_000,
                4000,
                100,
                100,
                100,
                500,
                1000,
                100,
                100,
                100
        );
    }

    public static WorkerIndexingProperties indexingProperties() {
        return new WorkerIndexingProperties(
                10000,
                4096,
                100,
                150,
                255,
                "bge-m3",
                1024,
                "ethnowear_chunks_bge_m3_v1"
        );
    }

    public static Document document(long id) {
        Document document = new Document();
        EntityTestUtils.setId(document, id);
        return document;
    }

    public static DocumentProcessingJob activePageExtractionJob(long id, Document document) {
        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, id);
        job.setJobType(JobType.PAGE_EXTRACTION);
        job.setStatus(JobStatus.RUNNING);
        job.setDocument(document);
        job.setAttemptCount(1);
        job.setMaxAttempts(3);
        job.setClaimedBy(WORKER_ID);
        job.assignClaimTokenHash(ContentHashUtils.sha256(CLAIM_TOKEN));
        job.setClaimExpiresAt(LocalDateTime.ofInstant(NOW.plusSeconds(120), ZoneOffset.UTC));
        job.setTimeoutAt(LocalDateTime.ofInstant(NOW.plusSeconds(1800), ZoneOffset.UTC));
        return job;
    }

    public static WorkerClaimedJobLoader loader(DocumentProcessingJob job) {
        DocumentProcessingJobRepository repository = RepositoryTestProxies.proxy(
                    DocumentProcessingJobRepository.class,
                    (ignored, method, arguments) -> {
                    if(method.getName().equals("findByIdForUpdate"))
                        return job.getId().equals(arguments[0])
                                ? Optional.of(job)
                                : Optional.empty();

                    throw new AssertionError("Unexpected repository call: " + method.getName());
                }
        );

        return new WorkerClaimedJobLoader(
                repository,
                new WorkerClaimValidator(),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }
}
