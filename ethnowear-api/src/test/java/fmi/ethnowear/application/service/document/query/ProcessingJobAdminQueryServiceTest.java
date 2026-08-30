package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.processing.ProcessingJobQueryDto;
import fmi.ethnowear.application.service.document.query.mapper.ProcessingJobAdminMapper;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.projection.document.ProcessingJobStatusCountProjection;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class ProcessingJobAdminQueryServiceTest {

    @Test
    void listsSafeJobContextWithNormalizedFilters() {
        DocumentProcessingJob job = job();
        AtomicReference<Object[]> arguments = new AtomicReference<>();
        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, values) -> {
                    if (!method.getName().equals("findAdminJobs"))
                        throw new AssertionError("Unexpected repository call: " + method.getName());

                    arguments.set(values);
                    return new PageImpl<>(List.of(job), (PageRequest) values[5], 1);
                }
        );
        ProcessingJobAdminQueryService service = new ProcessingJobAdminQueryService(
                repository,
                new ProcessingJobAdminMapper()
        );

        var result = service.findAll(
                new ProcessingJobQueryDto(
                        "  OCR ERROR  ",
                        JobType.OCR,
                        JobStatus.FAILED,
                        10L,
                        20L
                ),
                PageRequest.of(0, 25, Sort.by(Sort.Direction.DESC, "createdAt"))
        );

        assertEquals("%ocr error%", arguments.get()[0]);
        assertEquals(1, result.getTotalElements());
        assertEquals("Book", result.getContent().getFirst().document().title());
        assertEquals(4, result.getContent().getFirst().page().pageSequence());
        assertEquals("OCR_FAILED", result.getContent().getFirst().error().code());
        assertTrue(result.getContent().getFirst().progress().retryable());
        assertFalse(result.getContent().getFirst().progress().cancellable());
    }

    @Test
    void aggregatesEveryStatusAndRejectsUnknownSorts() {
        DocumentProcessingJobRepository repository = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, values) -> {
                    if (method.getName().equals("countAdminJobsByStatus"))
                        return List.of(
                                count(JobStatus.RUNNING, 2),
                                count(JobStatus.SUCCEEDED, 2),
                                count(JobStatus.FAILED, 3),
                                count(JobStatus.DEAD, 1)
                        );

                    throw new AssertionError("Unexpected repository call: " + method.getName());
                }
        );
        ProcessingJobAdminQueryService service = new ProcessingJobAdminQueryService(
                repository,
                new ProcessingJobAdminMapper()
        );

        var counts = service.counts(null);

        assertEquals(8, counts.total());
        assertEquals(2, counts.active());
        assertEquals(6, counts.retryable());
        assertEquals(2, counts.byStatus().get(JobStatus.SUCCEEDED));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findAll(
                        null,
                        PageRequest.of(0, 25, Sort.by("errorDetailsJson"))
                )
        );
    }

    private ProcessingJobStatusCountProjection count(JobStatus status, long total) {
        return new ProcessingJobStatusCountProjection() {
            @Override
            public JobStatus getStatus() {
                return status;
            }

            @Override
            public long getTotal() {
                return total;
            }
        };
    }

    private DocumentProcessingJob job() {
        Document document = new Document();
        EntityTestUtils.setId(document, 10L);
        document.setTitle("Book");
        document.setLanguage("bg");

        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 20L);
        page.setDocument(document);
        page.setPageSequence(4);
        page.setPdfPageIndex(3);

        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 30L);
        job.setJobType(JobType.OCR);
        job.setStatus(JobStatus.FAILED);
        job.setDocumentPage(page);
        job.setAttemptCount(2);
        job.setMaxAttempts(3);
        job.setErrorCode("OCR_FAILED");
        job.setSafeErrorMessage("OCR could not read the page");
        job.setErrorDetailsJson("{\"internal\":true}");
        return job;
    }
}
