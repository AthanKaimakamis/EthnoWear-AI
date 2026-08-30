package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.job.WorkerJobClaimCommand;
import fmi.ethnowear.application.dto.worker.job.WorkerJobClaimDetails;
import fmi.ethnowear.application.dto.worker.job.WorkerJobType;
import fmi.ethnowear.application.model.worker.ClaimedWorkerJob;
import fmi.ethnowear.application.port.worker.WorkerJobClaimStore;
import fmi.ethnowear.application.service.worker.security.WorkerClaimTokenService;
import fmi.ethnowear.config.FigureExtractionProperties;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.util.unit.DataSize;

import static fmi.ethnowear.support.WorkerTestFixtures.NOW;
import static fmi.ethnowear.support.WorkerTestFixtures.indexingProperties;
import static fmi.ethnowear.support.WorkerTestFixtures.properties;
import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class WorkerJobClaimServiceTest {

    @Test
    void returnsOpaqueAttemptTokenAndConfiguredLimits() {
        AtomicReference<String> storedHash = new AtomicReference<>();
        WorkerJobClaimStore store = (
                types,
                workerId,
                claimedAt,
                leaseExpiresAt,
                timeoutAt,
                claimTokenHash
        ) -> {
            assertEquals(Set.of(
                    JobType.PAGE_EXTRACTION,
                    JobType.OCR,
                    JobType.OCR_QUALITY_ASSESSMENT
            ), types);
            assertEquals("page-extractor-1", workerId);
            assertEquals(LocalDateTime.ofInstant(NOW, ZoneOffset.UTC), claimedAt);
            assertEquals(claimedAt.plusSeconds(120), leaseExpiresAt);
            assertEquals(claimedAt.plusMinutes(30), timeoutAt);
            storedHash.set(claimTokenHash);
            return Optional.of(new ClaimedWorkerJob(
                    11L,
                    JobType.PAGE_EXTRACTION,
                    7L,
                    21L,
                    null,
                    true,
                    2,
                    claimedAt,
                    leaseExpiresAt
            ));
        };
        WorkerJobClaimService service = new WorkerJobClaimService(
                store,
                new WorkerClaimTokenService(),
                properties(),
                indexingProperties(),
                figureProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                pageRepository(4)
        );

        WorkerJobClaimDetails result = service.claim(command()).orElseThrow();

        assertFalse(result.claimToken().isBlank());
        assertEquals(ContentHashUtils.sha256(result.claimToken()), storedHash.get());
        assertEquals(2, result.attempt());
        assertEquals(4, result.target().pdfPageIndex());
        assertEquals(250L * 1024 * 1024, result.limits().maximumInputBytes());
        assertEquals(300, result.limits().renderDpi());
        assertEquals(100000000, result.limits().maximumPagePixels());
        assertEquals(2_000_000, result.limits().maximumOcrTextCharacters());
        assertEquals(10L * 1024 * 1024, result.limits().maximumOcrOutputBytes());
        assertEquals(16L * 1024 * 1024, result.limits().maximumOcrContextBytes());
        assertEquals(100, result.limits().maximumQualitySignals());
        assertEquals(100, result.limits().maximumVisionIssues());
        assertEquals(100, result.limits().maximumVisionUncertainPassages());
        assertEquals(100, result.limits().maximumVisionIssueCodeCharacters());
        assertEquals(500, result.limits().maximumVisionExcerptCharacters());
        assertEquals(1000, result.limits().maximumVisionReasonCharacters());
        assertEquals(JobType.OCR, WorkerJobType.OCR.toDomainType());
        assertEquals(
                JobType.OCR_QUALITY_ASSESSMENT,
                WorkerJobType.OCR_QUALITY_ASSESSMENT.toDomainType()
        );
    }

    @Test
    void returnsEmptyWhenAtomicStoreFindsNoJobAndRejectsInvalidLease() {
        WorkerJobClaimStore emptyStore = (
                types,
                workerId,
                claimedAt,
                leaseExpiresAt,
                timeoutAt,
                claimTokenHash
        ) -> Optional.empty();
        WorkerJobClaimService service = new WorkerJobClaimService(
                emptyStore,
                new WorkerClaimTokenService(),
                properties(),
                indexingProperties(),
                figureProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                null
        );

        assertTrue(service.claim(command()).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> service.claim(new WorkerJobClaimCommand(
                        "page-extractor-1",
                        Set.of(WorkerJobType.PAGE_EXTRACTION),
                        10
                ))
        );
    }

    @Test
    void claimsOnlyIndexChunkAndReturnsKnowledgeChunkTarget() {
        WorkerJobClaimStore store = (
                types,
                workerId,
                claimedAt,
                leaseExpiresAt,
                timeoutAt,
                claimTokenHash
        ) -> {
            assertEquals(Set.of(JobType.INDEX_CHUNK), types);
            return Optional.of(new ClaimedWorkerJob(
                    12L,
                    JobType.INDEX_CHUNK,
                    7L,
                    null,
                    10001L,
                    false,
                    1,
                    claimedAt,
                    leaseExpiresAt
            ));
        };
        WorkerJobClaimService service = new WorkerJobClaimService(
                store,
                new WorkerClaimTokenService(),
                properties(),
                indexingProperties(),
                figureProperties(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                null
        );

        WorkerJobClaimDetails result = service.claim(new WorkerJobClaimCommand(
                "indexer-1",
                Set.of(WorkerJobType.INDEX_CHUNK),
                120
        )).orElseThrow();

        assertEquals(WorkerJobType.INDEX_CHUNK, result.jobType());
        assertEquals(10001L, result.target().knowledgeChunkId());
        assertFalse(result.target().inputAvailable());
        assertEquals(10000, result.limits().maximumIndexingContentCharacters());
        assertEquals(4096, result.limits().maximumEmbeddingDimensions());
    }

    private WorkerJobClaimCommand command() {
        return new WorkerJobClaimCommand(
                "page-extractor-1",
                Set.of(
                        WorkerJobType.PAGE_EXTRACTION,
                        WorkerJobType.OCR,
                        WorkerJobType.OCR_QUALITY_ASSESSMENT
                ),
                120
        );
    }

    private DocumentPageRepository pageRepository(int pdfPageIndex) {
        DocumentPage page = new DocumentPage();
        page.setPdfPageIndex(pdfPageIndex);

        return proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> Optional.of(page)
        );
    }

    private FigureExtractionProperties figureProperties() {
        return new FigureExtractionProperties(100, 2000, 100, DataSize.ofMegabytes(25));
    }
}
