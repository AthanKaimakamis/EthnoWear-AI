package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.extraction.PageExtractionManifestCommand;
import fmi.ethnowear.application.dto.worker.extraction.PageExtractionManifestDetails;
import fmi.ethnowear.application.dto.worker.extraction.PageExtractionManifestPageCommand;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.domain.model.document.DocumentPageRenditionType;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.application.service.document.upload.DocumentPageProvenanceRecorder;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.WorkerTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class PageExtractionManifestServiceTest {

    @Test
    void acceptsRepeatedIdenticalManifestAndReportsMissingRenditions() {
        Document document = document(7L);
        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        List<DocumentPage> pages = List.of(page(document, 21L, 0, 1), page(document, 22L, 1, 2));
        PageExtractionManifestService service = service(job, pages, List.of(21L));

        PageExtractionManifestDetails result = service.reconcile(11L, credentials(), command(2));

        assertEquals(2, result.pageCount());
        assertFalse(result.pages().getFirst().renditionRequired());
        assertTrue(result.pages().getLast().renditionRequired());
    }

    @Test
    void job111147RequiresOnlyItsTargetPageDespiteAnOlderRendition() {
        Document document = document(7L);
        DocumentPage target = page(document, 21L, 0, 1);
        DocumentPage other = page(document, 22L, 1, 2);
        DocumentProcessingJob job = activePageExtractionJob(111147L, document);
        job.setDocumentPage(target);

        PageExtractionManifestDetails result = service(
                job,
                List.of(target, other),
                List.of(21L, 22L)
        ).reconcile(111147L, credentials(), command(2));

        assertTrue(result.pages().getFirst().renditionRequired());
        assertFalse(result.pages().getLast().renditionRequired());
    }

    @Test
    void rejectsConflictingRepeatedManifest() {
        Document document = document(7L);
        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        List<DocumentPage> pages = List.of(page(document, 21L, 0, 2), page(document, 22L, 1, 1));

        assertThrows(
                WorkerManifestConflictException.class,
                () -> service(job, pages, List.of()).reconcile(11L, credentials(), command(2))
        );
    }

    @Test
    void rejectsManifestBeyondConfiguredLimit() {
        Document document = document(7L);
        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        PageExtractionManifestCommand command = new PageExtractionManifestCommand(
                2001,
                List.of(new PageExtractionManifestPageCommand(0, 1))
        );

        assertThrows(
                WorkerPayloadTooLargeException.class,
                () -> service(job, List.of(), List.of()).reconcile(11L, credentials(), command)
        );
    }

    @Test
    void generatedPagesInheritTheDocumentDefaultSourceReference() {
        Document document = document(7L);
        SourceReference sourceReference = new SourceReference();
        EntityTestUtils.setId(sourceReference, 31L);
        document.setDefaultSourceReference(sourceReference);
        DocumentProcessingJob job = activePageExtractionJob(11L, document);
        DocumentPageProvenanceRecorder recorder = mock(
                DocumentPageProvenanceRecorder.class
        );

        PageExtractionManifestDetails result = service(
                job,
                List.of(),
                List.of(),
                recorder
        ).reconcile(11L, credentials(), command(2));

        assertEquals(2, result.pageCount());
        verify(recorder, times(2)).recordInitial(
                org.mockito.ArgumentMatchers.any(DocumentPage.class),
                org.mockito.ArgumentMatchers.same(sourceReference),
                org.mockito.ArgumentMatchers.eq(document.getProvenanceStatus()),
                org.mockito.ArgumentMatchers.eq(document.getProvenanceTrustState()),
                org.mockito.ArgumentMatchers.eq("system:page-extraction"),
                org.mockito.ArgumentMatchers.eq(
                        "Inherited from the document default source reference"
                )
        );
    }

    private PageExtractionManifestService service(
            DocumentProcessingJob job,
            List<DocumentPage> pages,
            List<Long> renderedPageIds
    ) {
        return service(
                job,
                pages,
                renderedPageIds,
                mock(DocumentPageProvenanceRecorder.class)
        );
    }

    private PageExtractionManifestService service(
            DocumentProcessingJob job,
            List<DocumentPage> pages,
            List<Long> renderedPageIds,
            DocumentPageProvenanceRecorder provenanceRecorder
    ) {
        DocumentPageRepository pageRepository = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "findByDocument_IdOrderByPageSequenceAsc" -> pages;
                    case "saveAllAndFlush" -> {
                        @SuppressWarnings("unchecked")
                        List<DocumentPage> saved = (List<DocumentPage>) arguments[0];
                        for(int index = 0; index < saved.size(); index++)
                            EntityTestUtils.setId(saved.get(index), 21L + index);
                        yield saved;
                    }
                    default -> throw new AssertionError(
                            "Unexpected page repository call: " + method.getName()
                    );
                }
        );
        DocumentPageMediaRepository mediaRepository = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findPageIdsWithRendition")) {
                        assertEquals(DocumentPageRenditionType.PDF_PAGE_RENDER, arguments[1]);
                        return renderedPageIds;
                    }

                    throw new AssertionError("Unexpected media repository call: " + method.getName());
                }
        );

        return new PageExtractionManifestService(
                pageRepository,
                mediaRepository,
                properties(),
                loader(job),
                fmi.ethnowear.support.ManagementEventTestSupport.events(),
                provenanceRecorder
        );
    }

    private PageExtractionManifestCommand command(int pageCount) {
        return new PageExtractionManifestCommand(
                pageCount,
                List.of(
                        new PageExtractionManifestPageCommand(0, 1),
                        new PageExtractionManifestPageCommand(1, 2)
                )
        );
    }

    private DocumentPage page(Document document, long id, int pdfIndex, int sequence) {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, id);
        page.setDocument(document);
        page.setPdfPageIndex(pdfIndex);
        page.setPageSequence(sequence);
        return page;
    }
}
