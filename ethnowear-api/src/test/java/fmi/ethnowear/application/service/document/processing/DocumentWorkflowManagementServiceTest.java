package fmi.ethnowear.application.service.document.processing;

import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.exception.InvalidDocumentJobTransitionException;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.RowVersionUtils;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentWorkflowManagementServiceTest {

    @Test
    void rejectsRetiringPageWhileItHasActiveProcessing() {
        DocumentPage page = page();
        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> true
        );

        assertThrows(
                DocumentDependencyConflictException.class,
                () -> service(pageRepository(page), jobs).retirePage(
                        7L,
                        9L,
                        RowVersionUtils.token(page.getRowVersion()),
                        "Duplicate page",
                        "admin"
                )
        );
    }

    @Test
    void rejectsStalePageMutationBeforeCheckingDependencies() {
        DocumentPage page = page();
        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> {
                    throw new AssertionError("Dependencies must not be queried");
                }
        );

        assertThrows(
                RowVersionUtils.StaleRowVersionException.class,
                () -> service(pageRepository(page), jobs).retirePage(
                        7L,
                        9L,
                        "AQ",
                        "Duplicate page",
                        "admin"
                )
        );
    }

    @Test
    void rejectsRetiringSuccessfulJob() {
        DocumentProcessingJob job = new DocumentProcessingJob();
        EntityTestUtils.setId(job, 11L);
        job.setStatus(JobStatus.SUCCEEDED);
        ReflectionTestUtils.setField(
                job,
                "rowVersion",
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
        );
        DocumentProcessingJobRepository jobs = proxy(
                DocumentProcessingJobRepository.class,
                (ignored, method, arguments) -> Optional.of(job)
        );

        assertThrows(
                InvalidDocumentJobTransitionException.class,
                () -> service(null, jobs).retireJob(
                        11L,
                        RowVersionUtils.token(job.getRowVersion()),
                        "Obsolete job",
                        "admin"
                )
        );
    }

    private DocumentWorkflowManagementService service(
            DocumentPageRepository pages,
            DocumentProcessingJobRepository jobs
    ) {
        return new DocumentWorkflowManagementService(
                pages,
                null,
                null,
                null,
                jobs,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Clock.fixed(Instant.parse("2026-08-24T00:00:00Z"), ZoneOffset.UTC)
        );
    }

    private DocumentPageRepository pageRepository(DocumentPage page) {
        return proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> Optional.of(page)
        );
    }

    private DocumentPage page() {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 9L);
        Document document = new Document();
        EntityTestUtils.setId(document, 7L);
        page.setDocument(document);
        ReflectionTestUtils.setField(
                page,
                "rowVersion",
                new byte[]{1, 2, 3, 4, 5, 6, 7, 8}
        );
        return page;
    }
}
