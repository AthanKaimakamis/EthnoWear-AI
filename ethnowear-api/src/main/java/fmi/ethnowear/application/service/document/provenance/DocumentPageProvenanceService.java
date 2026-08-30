package fmi.ethnowear.application.service.document.provenance;

import fmi.ethnowear.application.dto.document.command.provenance.CanonicalPageLinkCommand;
import fmi.ethnowear.application.dto.document.command.provenance.PageProvenanceTrustChangeCommand;
import fmi.ethnowear.application.dto.document.command.provenance.PageSourceProvenanceChangeCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageProvenanceEventDetails;
import fmi.ethnowear.application.exception.InvalidDocumentPageProvenanceTransitionException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.document.review.DocumentPageChunkInvalidator;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceEventType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageProvenanceEvent;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageProvenanceEventRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class DocumentPageProvenanceService {

    private final DocumentPageRepository pageRepository;
    private final SourceReferenceRepository sourceReferenceRepository;
    private final DocumentPageProvenanceEventRepository eventRepository;
    private final DocumentHistoryMapper historyMapper;
    private final ManagementEventPublisher managementEvents;
    private final DocumentPageChunkInvalidator chunkInvalidator;

    @Transactional
    public DocumentPageProvenanceEventDetails changeSource(
            Long pageId,
            PageSourceProvenanceChangeCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if (command == null)
            throw new IllegalArgumentException("Page source provenance command is required");

        validateReason(command.reason());
        validateProvenance(
                command.sourceReferenceId(),
                command.provenanceStatus(),
                command.provenanceTrustState()
        );

        DocumentPage page = requirePage(pageId);
        ProvenanceSnapshot previous = snapshot(page);

        SourceReference sourceReference = resolveSourceReference(
                command.sourceReferenceId()
        );

        if (Objects.equals(id(page.getSourceReference()), id(sourceReference))
                && page.getProvenanceStatus() == command.provenanceStatus()
                && page.getProvenanceTrustState()
                == command.provenanceTrustState()
                && Objects.equals(page.getProvenanceNote(), command.note()))
            throw new InvalidDocumentPageProvenanceTransitionException(
                    "Page source provenance is unchanged"
            );

        ProvenanceEventType eventType = page
                .getSourceReference() == null && sourceReference != null
                        ? ProvenanceEventType.SOURCE_IDENTIFIED
                        : ProvenanceEventType.SOURCE_REFERENCE_CHANGED;

        page.setSourceReference(sourceReference);
        page.setProvenanceStatus(command.provenanceStatus());
        page.setProvenanceTrustState(command.provenanceTrustState());
        page.setProvenanceNote(command.note());

        return saveChange(
                page,
                eventType,
                previous,
                reviewer,
                command.reason()
        );
    }

    @Transactional
    public DocumentPageProvenanceEventDetails changeTrust(
            Long pageId,
            PageProvenanceTrustChangeCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if (command == null)
            throw new IllegalArgumentException("Page provenance trust command is required");

        validateReason(command.reason());

        DocumentPage page = requirePage(pageId);
        ProvenanceSnapshot previous = snapshot(page);

        inheritDocumentSourceReference(page);

        validateProvenance(
                id(page.getSourceReference()),
                page.getProvenanceStatus(),
                command.provenanceTrustState()
        );

        if (page.getProvenanceTrustState() == command.provenanceTrustState())
            throw new InvalidDocumentPageProvenanceTransitionException(
                    "Page provenance trust state is unchanged"
            );

        page.setProvenanceTrustState(command.provenanceTrustState());

        return saveChange(
                page,
                ProvenanceEventType.TRUST_CHANGED,
                previous,
                reviewer,
                command.reason()
        );
    }

    private void inheritDocumentSourceReference(DocumentPage page) {
        if (page.getSourceReference() != null
                || page.getDocument() == null
                || page.getDocument().getDefaultSourceReference() == null)
            return;

        page.setSourceReference(page.getDocument().getDefaultSourceReference());
        page.setProvenanceStatus(page.getDocument().getProvenanceStatus());
    }

    @Transactional
    public DocumentPageProvenanceEventDetails linkCanonicalPage(
            Long pageId,
            CanonicalPageLinkCommand command,
            String reviewer
    ) {
        validateCanonicalCommand(pageId, command, reviewer);

        DocumentPage page = requirePage(pageId);
        DocumentPage canonicalPage = requirePage(
                command.canonicalDocumentPageId()
        );

        validateCanonicalTarget(page, canonicalPage);

        if (page.getCanonicalDocumentPage() != null)
            throw new InvalidDocumentPageProvenanceTransitionException(
                    "Document page is already linked to a canonical page"
            );

        ProvenanceSnapshot previous = snapshot(page);
        page.setCanonicalDocumentPage(canonicalPage);

        return saveChange(
                page,
                ProvenanceEventType.LINKED_TO_CANONICAL_PAGE,
                previous,
                reviewer,
                command.reason()
        );
    }

    @Transactional
    public DocumentPageProvenanceEventDetails mergeIntoCanonicalPage(
            Long pageId,
            CanonicalPageLinkCommand command,
            String reviewer
    ) {
        validateCanonicalCommand(pageId, command, reviewer);

        DocumentPage page = requirePage(pageId);
        DocumentPage canonicalPage = requirePage(
                command.canonicalDocumentPageId()
        );

        validateCanonicalTarget(page, canonicalPage);

        if (page.getEvidenceState() == EvidenceState.MERGED)
            throw new InvalidDocumentPageProvenanceTransitionException(
                    "Document page is already merged"
            );

        ProvenanceSnapshot previous = snapshot(page);

        page.setCanonicalDocumentPage(canonicalPage);
        page.setEvidenceState(EvidenceState.MERGED);
        page.setSourceReference(canonicalPage.getSourceReference());
        page.setProvenanceStatus(canonicalPage.getProvenanceStatus());
        page.setProvenanceTrustState(
                canonicalPage.getProvenanceTrustState()
        );

        return saveChange(
                page,
                ProvenanceEventType.MERGED,
                previous,
                reviewer,
                command.reason()
        );
    }

    @Transactional
    public DocumentPageProvenanceEventDetails reverseCanonicalLink(
            Long pageId,
            String reason,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);
        validateReason(reason);

        DocumentPage page = requirePage(pageId);

        if (page.getCanonicalDocumentPage() == null)
            throw new InvalidDocumentPageProvenanceTransitionException(
                    "Document page is not linked to a canonical page"
            );

        ProvenanceSnapshot previous = snapshot(page);

        page.setCanonicalDocumentPage(null);

        if (page.getEvidenceState() == EvidenceState.MERGED)
            page.setEvidenceState(EvidenceState.ACTIVE);

        return saveChange(
                page,
                ProvenanceEventType.LINK_REVERSED,
                previous,
                reviewer,
                reason
        );
    }

    private DocumentPageProvenanceEventDetails saveChange(
            @NonNull DocumentPage page,
            ProvenanceEventType eventType,
            @NonNull ProvenanceSnapshot previous,
            @NonNull String reviewer,
            @NonNull String reason
    ) {
        page.setProvenanceReviewedBy(reviewer.trim());
        page.setProvenanceReviewedAt(LocalDateTime.now(ZoneOffset.UTC));

        chunkInvalidator.invalidate(page);
        pageRepository.save(page);

        DocumentPageProvenanceEvent event = new DocumentPageProvenanceEvent();

        event.setDocumentPage(page);
        event.setEventType(eventType);
        event.setPreviousSourceReference(previous.sourceReference());
        event.setNewSourceReference(page.getSourceReference());
        event.setPreviousProvenanceStatus(previous.provenanceStatus());
        event.setNewProvenanceStatus(page.getProvenanceStatus());
        event.setPreviousTrustState(previous.trustState());
        event.setNewTrustState(page.getProvenanceTrustState());
        event.setPreviousCanonicalDocumentPage(previous.canonicalPage());
        event.setNewCanonicalDocumentPage(page.getCanonicalDocumentPage());
        event.setReviewedBy(reviewer.trim());
        event.setReason(reason.trim());

        DocumentPageProvenanceEvent savedEvent = eventRepository.saveAndFlush(event);
        managementEvents.page(page, ManagementEvent.Action.STATUS_CHANGED);

        return historyMapper.toDetails(savedEvent);
    }

    private void validateCanonicalCommand(
            Long pageId,
            CanonicalPageLinkCommand command,
            String reviewer
    ) {
        validatePageId(pageId);
        validateReviewer(reviewer);

        if (command == null || command.canonicalDocumentPageId() == null)
            throw new IllegalArgumentException("Canonical document page id is required");

        validatePageId(command.canonicalDocumentPageId());
        validateReason(command.reason());
    }

    private void validateCanonicalTarget(
            @NonNull DocumentPage page,
            @NonNull DocumentPage canonicalPage
    ) {
        if (Objects.equals(page.getId(), canonicalPage.getId()))
            throw new InvalidDocumentPageProvenanceTransitionException(
                    "A document page cannot reference itself as canonical"
            );

        if (canonicalPage.getCanonicalDocumentPage() != null
                || canonicalPage.getEvidenceState() == EvidenceState.MERGED)
            throw new InvalidDocumentPageProvenanceTransitionException(
                    "The selected page is not a canonical evidence page"
            );
    }

    private void validateProvenance(
            Long sourceReferenceId,
            ProvenanceStatus status,
            ProvenanceTrustState trustState
    ) {
        if (status == null)
            throw new IllegalArgumentException("Provenance status is required");

        if (trustState == null)
            throw new IllegalArgumentException("Provenance trust state is required");

        if (status == ProvenanceStatus.KNOWN_SOURCE && sourceReferenceId == null)
            throw new IllegalArgumentException("Known provenance requires a source reference");

        if (status == ProvenanceStatus.UNKNOWN_SOURCE && sourceReferenceId != null)
            throw new IllegalArgumentException("Unknown provenance cannot reference a source");

        if (status == ProvenanceStatus.UNKNOWN_SOURCE
                && (trustState == ProvenanceTrustState.TRUSTED
                || trustState == ProvenanceTrustState.VERIFIED))
            throw new IllegalArgumentException("Unknown provenance cannot be trusted or verified");
    }

    private SourceReference resolveSourceReference(Long sourceReferenceId) {
        if (sourceReferenceId == null)
            return null;

        return sourceReferenceRepository.findById(sourceReferenceId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Source reference",
                        sourceReferenceId
                ));
    }

    private @NonNull DocumentPage requirePage(Long pageId) {
        return pageRepository.findByIdForUpdate(pageId)
                .orElseThrow(() -> new ResourceNotFoundException("Document page", pageId));
    }

    @Contract("_ -> new")
    private @NonNull ProvenanceSnapshot snapshot(
            @NonNull DocumentPage page
    ) {
        return new ProvenanceSnapshot(
                page.getSourceReference(),
                page.getProvenanceStatus(),
                page.getProvenanceTrustState(),
                page.getCanonicalDocumentPage()
        );
    }

    private Long id(SourceReference sourceReference) {
        return sourceReference == null
                ? null
                : sourceReference.getId();
    }

    private void validatePageId(Long pageId) {
        if (pageId == null || pageId <= 0)
            throw new IllegalArgumentException("Valid document page id is required");
    }

    private void validateReason(String reason) {
        if (isBlank(reason))
            throw new IllegalArgumentException("Provenance change reason is required");

        if (reason.trim().length() > 1000)
            throw new IllegalArgumentException("Provenance change reason cannot exceed 1000 characters");
    }

    private void validateReviewer(String reviewer) {
        if (isBlank(reviewer))
            throw new IllegalArgumentException("Authenticated provenance reviewer is required");

        if (reviewer.trim().length() > 150)
            throw new IllegalArgumentException("Provenance reviewer cannot exceed 150 characters");
    }

    private record ProvenanceSnapshot(
            SourceReference sourceReference,
            ProvenanceStatus provenanceStatus,
            ProvenanceTrustState trustState,
            DocumentPage canonicalPage
    ) {
    }
}
