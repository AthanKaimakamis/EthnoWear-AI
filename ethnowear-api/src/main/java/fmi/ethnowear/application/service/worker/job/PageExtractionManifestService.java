package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.worker.extraction.*;
import fmi.ethnowear.application.exception.WorkerClaimConflictException;
import fmi.ethnowear.application.exception.WorkerManifestConflictException;
import fmi.ethnowear.application.exception.WorkerPayloadTooLargeException;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.document.upload.DocumentPageProvenanceRecorder;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
public class PageExtractionManifestService {

    private final DocumentPageRepository pageRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final WorkerApiProperties properties;
    private final WorkerClaimedJobLoader jobLoader;
    private final ManagementEventPublisher managementEvents;
    private final DocumentPageProvenanceRecorder provenanceRecorder;

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
        boolean pagesCreated = pages.isEmpty();

        if (pagesCreated)
            pages = createPages(document, entries);
        else
            validateExistingPages(pages, entries);

        Long targetPageId = targetPageId(job, document);
        Set<Long> renderedPageIds = targetPageId == null
                ? new HashSet<>(pageMediaRepository.findPageIdsWithRendition(
                pages.stream().map(DocumentPage::getId).toList(),
                DocumentPageRenditionType.PDF_PAGE_RENDER
        ))
                : Set.of();

        List<PageExtractionManifestPageDetails> details = pages.stream()
                .map(page -> new PageExtractionManifestPageDetails(
                        page.getId(),
                        page.getPdfPageIndex(),
                        page.getPageSequence(),
                        targetPageId == null
                                ? !renderedPageIds.contains(page.getId())
                                : Objects.equals(page.getId(), targetPageId)
                ))
                .toList();

        if (pagesCreated)
            managementEvents.document(
                    document,
                    ManagementEvent.Action.UPDATED
            );

        return new PageExtractionManifestDetails(
                document.getId(),
                details.size(),
                details
        );
    }

    private Long targetPageId(
            @NonNull DocumentProcessingJob job,
            @NonNull Document document
    ) {
        DocumentPage target = job.getDocumentPage();

        if (target == null)
            return null;

        if (target.getDocument() == null
                || !Objects.equals(target.getDocument().getId(), document.getId()))
            throw new WorkerManifestConflictException(
                    "Page-extraction target does not belong to the job document"
            );

        return target.getId();
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

        List<DocumentPage> savedPages = pageRepository.saveAllAndFlush(pages);

        if(document.getDefaultSourceReference() != null)
            savedPages.forEach(page -> provenanceRecorder.recordInitial(
                    page,
                    document.getDefaultSourceReference(),
                    document.getProvenanceStatus(),
                    document.getProvenanceTrustState(),
                    "system:page-extraction",
                    "Inherited from the document default source reference"
            ));

        return savedPages;
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
        page.setSourceReference(document.getDefaultSourceReference());
        page.setProvenanceStatus(document.getProvenanceStatus());
        page.setProvenanceTrustState(document.getProvenanceTrustState());

        if(document.getDefaultSourceReference() != null) {
            page.setProvenanceReviewedBy("system:page-extraction");
            page.setProvenanceReviewedAt(LocalDateTime.now(ZoneOffset.UTC));
        }

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
