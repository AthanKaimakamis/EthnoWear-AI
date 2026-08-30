package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.dto.document.query.chunk.ChunkGenerationBlockerDetails;
import fmi.ethnowear.application.dto.document.query.chunk.ChunkGenerationEligibilityDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.model.document.chunk.ChunkGenerationInput;
import fmi.ethnowear.application.model.document.chunk.EligibleChunkPage;
import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class DocumentChunkGenerationEligibilityService {

    private final DocumentRepository documentRepository;
    private final DocumentPageRepository pageRepository;
    private final DocumentChunkGenerationInputHasher inputHasher;
    private final DocumentChunkingProperties properties;

    @Transactional(readOnly = true)
    public ChunkGenerationInput requireInput(Long documentId) {
        if(documentId == null)
            throw new IllegalArgumentException("Document id is required");

        validateConfiguration();

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document",
                        documentId
                ));
        List<EligibleChunkPage> eligiblePages = new ArrayList<>();
        List<ChunkGenerationBlockerDetails> blockers = new ArrayList<>();

        pageRepository.findChunkGenerationCandidates(documentId)
                .forEach(page -> evaluate(
                        document,
                        page,
                        eligiblePages,
                        blockers
                ));

        String inputHash = eligiblePages.isEmpty()
                ? null
                : inputHasher.hash(eligiblePages, properties);

        return new ChunkGenerationInput(
                document,
                inputHash,
                eligiblePages,
                blockers
        );
    }

    @Transactional(readOnly = true)
    public ChunkGenerationInput requirePageInput(
            Long documentId,
            Long pageId
    ) {
        if (pageId == null)
            throw new IllegalArgumentException("Document page id is required");

        ChunkGenerationInput documentInput = requireInput(documentId);
        List<EligibleChunkPage> pages = documentInput.pages().stream()
                .filter(candidate -> pageId.equals(candidate.page().getId()))
                .toList();

        if (pages.isEmpty())
            throw new InvalidDocumentProcessingRequestException(
                    "Document page is not eligible for chunk generation"
            );

        return new ChunkGenerationInput(
                documentInput.document(),
                inputHasher.hash(pages, properties),
                pages,
                List.of()
        );
    }

    @Transactional(readOnly = true)
    public ChunkGenerationEligibilityDetails eligibility(Long documentId) {
        ChunkGenerationInput input = requireInput(documentId);

        return new ChunkGenerationEligibilityDetails(
                documentId,
                !input.pages().isEmpty(),
                input.pages().size(),
                input.blockers()
        );
    }

    @Transactional(readOnly = true)
    public boolean isPageEligible(Long documentId, Long pageId) {
        if (pageId == null)
            throw new IllegalArgumentException("Document page id is required");

        return requireInput(documentId).pages().stream()
                .anyMatch(candidate -> pageId.equals(candidate.page().getId()));
    }

    private void evaluate(
            Document document,
            DocumentPage page,
            List<EligibleChunkPage> eligiblePages,
            List<ChunkGenerationBlockerDetails> blockers
    ) {
        ChunkGenerationBlockerDetails blocker = blocker(document, page);

        if(blocker != null) {
            blockers.add(blocker);
            return;
        }

        eligiblePages.add(new EligibleChunkPage(
                page,
                page.getCorrectedText(),
                page.getCorrectedTextHash()
        ));
    }

    private ChunkGenerationBlockerDetails blocker(
            Document document,
            DocumentPage page
    ) {
        if(page.getEvidenceState() != EvidenceState.ACTIVE)
            return blocker(page, "PAGE_NOT_ACTIVE", "Page is not active");

        if(page.getProcessingState() != ProcessingState.COMPLETED)
            return blocker(page, "PROCESSING_INCOMPLETE", "Page processing is incomplete");

        if(page.getReviewState() != ReviewState.APPROVED
                || page.getTranscriptionApprovalState()
                != TranscriptionApprovalState.APPROVED)
            return blocker(page, "TRANSCRIPTION_NOT_APPROVED", "Corrected transcription is not approved");

        if(isBlank(page.getCorrectedText()))
            return blocker(page, "CORRECTED_TEXT_BLANK", "Corrected transcription is blank");

        if(!ContentHashUtils.sha256(page.getCorrectedText())
                .equals(page.getCorrectedTextHash()))
            return blocker(page, "CORRECTED_TEXT_HASH_MISMATCH", "Corrected transcription hash is invalid");

        if(!provenanceEligible(document, page))
            return blocker(page, "PROVENANCE_INELIGIBLE", "Page provenance is not eligible");

        return null;
    }

    private boolean provenanceEligible(
            Document document,
            DocumentPage page
    ) {
        if(document.getDocumentType() == DocumentType.STANDALONE_CAPTURE
                || document.getDocumentType() == DocumentType.UNKNOWN_FRAGMENT_SET)
            return page.getProvenanceTrustState()
                    != ProvenanceTrustState.UNTRUSTED;

        return page.getSourceReference() != null
                && (page.getProvenanceTrustState()
                == ProvenanceTrustState.TRUSTED
                || page.getProvenanceTrustState()
                == ProvenanceTrustState.VERIFIED);
    }

    private ChunkGenerationBlockerDetails blocker(
            DocumentPage page,
            String code,
            String message
    ) {
        return new ChunkGenerationBlockerDetails(page.getId(), code, message);
    }

    private void validateConfiguration() {
        if(properties.getMaximumChunkCharacters() < 100)
            throw new IllegalStateException(
                    "Maximum chunk size must be at least 100 characters"
            );

        if(properties.getOverlapCharacters() < 0
                || properties.getOverlapCharacters()
                >= properties.getMaximumChunkCharacters())
            throw new IllegalStateException(
                    "Chunk overlap must be non-negative and smaller than the maximum chunk size"
            );
    }
}
