package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.extraction.*;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.WorkerApiProperties;
import fmi.ethnowear.domain.model.document.*;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.document.*;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PageExtractionManifestService {

    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final WorkerApiProperties properties;
    private final WorkerClaimedJobLoader jobLoader;

    @Transactional
    public PageExtractionManifestDetails reconcile(
            Long jobId,
            WorkerClaimCredentials credentials,
            PageExtractionManifestCommand command
    ) {
        validateCommand(command);

        DocumentProcessingJob job = jobLoader.requireActive(jobId, credentials);

        if (job.getStatus() == JobStatus.CANCEL_REQUESTED)
            throw new WorkerClaimConflictException();

        if (job.getJobType() != JobType.PAGE_EXTRACTION)
            throw new WorkerManifestConflictException("Job is not a page-extraction job");

        Document document = job.getDocument();

        if (document == null)
            throw new WorkerManifestConflictException("Page-extraction job has no document");

        List<PageExtractionManifestPageCommand> entries = command.pages()
                .stream()
                .sorted(Comparator.comparingInt(PageExtractionManifestPageCommand::pageSequence))
                .toList();

        validateEntries(command.totalPageCount(), entries);

        List<DocumentPage> pages = pageRepository
                .findByDocument_IdOrderByPageSequenceAsc(document.getId());

        if (pages.isEmpty())
            pages = createPages(document, entries);
        else
            validateExistingPages(pages, entries);

        Set<Long> renderedPageIds = new HashSet<>(
                pageMediaRepository.findPageIdsWithRendition(
                        pages.stream().map(DocumentPage::getId).toList(),
                        DocumentPageRenditionType.PDF_PAGE_RENDER
                )
        );

        List<PageExtractionManifestPageDetails> details = pages.stream()
                .map(page -> new PageExtractionManifestPageDetails(
                        page.getId(),
                        page.getPdfPageIndex(),
                        page.getPageSequence(),
                        !renderedPageIds.contains(page.getId())
                ))
                .toList();

        return new PageExtractionManifestDetails(
                document.getId(),
                details.size(),
                details
        );
    }

    private void validateCommand(PageExtractionManifestCommand command) {
        if (command == null)
            throw new IllegalArgumentException("Page-extraction manifest is required");

        if (command.totalPageCount() > properties.maximumPageCount())
            throw new WorkerPayloadTooLargeException("Manifest exceeds the maximum page count");

        if (command.pages() == null || command.pages().size() != command.totalPageCount())
            throw new IllegalArgumentException("Manifest page count does not match its entries");
    }

    private void validateEntries(
            int totalPageCount,
            List<PageExtractionManifestPageCommand> entries
    ) {
        for (int index = 0; index < totalPageCount; index++) {
            PageExtractionManifestPageCommand entry = entries.get(index);

            if (entry.pdfPageIndex() != index
                    || entry.pageSequence() != index + 1)
                throw new IllegalArgumentException("Manifest pages must use contiguous indexes and sequences");
        }
    }

    private @NonNull List<DocumentPage> createPages(
            Document document,
            @NonNull List<PageExtractionManifestPageCommand> entries
    ) {
        List<DocumentPage> pages = entries.stream()
                .map(entry -> createPage(document, entry))
                .toList();

        return pageRepository.saveAllAndFlush(pages);
    }

    private @NonNull DocumentPage createPage(
            Document document,
            @NonNull PageExtractionManifestPageCommand entry
    ) {
        DocumentPage page = new DocumentPage();
        page.setDocument(document);
        page.setPageKind(PageKind.DOCUMENT_PAGE);
        page.setPageRole(PageRole.NORMAL);
        page.setPageSequence(entry.pageSequence());
        page.setPdfPageIndex(entry.pdfPageIndex());
        page.setProvenanceStatus(document.getProvenanceStatus());
        page.setProvenanceTrustState(document.getProvenanceTrustState());

        return page;
    }

    private void validateExistingPages(
            @NonNull List<DocumentPage> pages,
            @NonNull List<PageExtractionManifestPageCommand> entries
    ) {
        if (pages.size() != entries.size())
            throw new WorkerManifestConflictException("Manifest conflicts with existing document pages");

        for (int index = 0; index < pages.size(); index++) {
            DocumentPage page = pages.get(index);
            PageExtractionManifestPageCommand entry = entries.get(index);

            if (!Objects.equals(page.getPdfPageIndex(), entry.pdfPageIndex())
                    || !Objects.equals(page.getPageSequence(), entry.pageSequence()))
                throw new WorkerManifestConflictException("Manifest conflicts with existing page identities");
        }
    }
}
