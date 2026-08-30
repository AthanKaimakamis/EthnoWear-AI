package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.exception.InvalidDocumentProcessingRequestException;
import fmi.ethnowear.application.model.document.chunk.ChunkGenerationInput;
import fmi.ethnowear.application.model.document.chunk.ChunkGenerationResult;
import fmi.ethnowear.application.model.document.chunk.ChunkPageContribution;
import fmi.ethnowear.application.model.document.chunk.KnowledgeChunkDraft;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.application.service.document.indexing.DocumentIndexingStateReconciler;
import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.indexing.SourceTextType;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import fmi.ethnowear.util.ContentHashUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class DocumentChunkGenerationService {

    private final DocumentChunkGenerationEligibilityService eligibilityService;
    private final DeterministicPageAwareChunker chunker;
    private final KnowledgeChunkRepository chunkRepository;
    private final KnowledgeChunkPageRepository chunkPageRepository;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentChunkingProperties properties;
    private final DocumentIndexingStateReconciler indexingStateReconciler;

    @Transactional
    public ChunkGenerationResult generate(
            Long documentId,
            String expectedInputHash
    ) {
        Document lockedDocument = indexingStateReconciler.lockDocument(
                documentId
        );
        ChunkGenerationInput input = eligibilityService.requireInput(documentId);
        validateInput(input, expectedInputHash);

        List<KnowledgeChunk> existing = chunkRepository
                .findByDocument_IdAndGenerationInputHashOrderByChunkOrdinalAsc(
                        documentId,
                        input.generationInputHash()
                );

        if(!existing.isEmpty()) {
            indexingStateReconciler.reconcileLocked(lockedDocument);
            return new ChunkGenerationResult(
                    input.generationInputHash(),
                    true,
                    existing
            );
        }

        List<KnowledgeChunkDraft> drafts = chunker.chunk(input);
        if(drafts.isEmpty())
            throw new InvalidDocumentProcessingRequestException(
                    "No approved corrected text is available for chunk generation"
            );

        List<KnowledgeChunk> previous = chunkRepository
                .findByDocument_IdAndGenerationInputHashIsNotNullAndSupersededByIsNullOrderByChunkOrdinalAsc(
                        documentId
                );
        List<KnowledgeChunk> created = drafts.stream()
                .map(draft -> createChunk(input, draft))
                .toList();

        chunkRepository.saveAllAndFlush(created);
        savePageLinks(drafts, created);
        supersede(previous, created);
        indexingStateReconciler.reconcileLocked(lockedDocument);
        created.forEach(jobScheduler::queueIndexChunk);

        return new ChunkGenerationResult(
                input.generationInputHash(),
                false,
                created
        );
    }

    @Transactional
    public boolean generatePageIfRequired(
            Long documentId,
            Long pageId
    ) {
        Document lockedDocument = indexingStateReconciler.lockDocument(documentId);
        List<KnowledgeChunk> previous = chunkPageRepository
                .findCurrentByDocumentPageId(pageId)
                .stream()
                .map(KnowledgeChunkPage::getKnowledgeChunk)
                .distinct()
                .toList();

        boolean initialGeneration = previous.isEmpty();
        boolean replacementGeneration = previous.stream().anyMatch(chunk ->
                chunk.getIndexingState() == IndexingState.OUTDATED);

        if (!initialGeneration && !replacementGeneration)
            return false;

        ChunkGenerationInput eligibleInput = eligibilityService.requirePageInput(
                documentId,
                pageId
        );
        String generationInputHash = initialGeneration
                ? eligibleInput.generationInputHash()
                : ContentHashUtils.sha256(
                        eligibleInput.generationInputHash()
                                + ":PAGE_REPLACEMENT_AFTER:"
                                + previous.stream()
                                .mapToLong(KnowledgeChunk::getId)
                                .max()
                                .orElseThrow()
                );
        ChunkGenerationInput input = new ChunkGenerationInput(
                eligibleInput.document(),
                generationInputHash,
                eligibleInput.pages(),
                eligibleInput.blockers()
        );
        List<KnowledgeChunkDraft> drafts = chunker.chunk(input);

        if (drafts.isEmpty())
            throw new InvalidDocumentProcessingRequestException(
                    "No approved corrected text is available for page chunk generation"
            );

        List<KnowledgeChunk> created = drafts.stream()
                .map(draft -> createChunk(input, draft))
                .toList();
        chunkRepository.saveAllAndFlush(created);
        savePageLinks(drafts, created);
        supersede(previous, created);
        indexingStateReconciler.reconcileLocked(lockedDocument);
        created.forEach(jobScheduler::queueIndexChunk);
        return true;
    }

    private void validateInput(
            ChunkGenerationInput input,
            String expectedInputHash
    ) {
        if(input.pages().isEmpty())
            throw new InvalidDocumentProcessingRequestException(
                    "Document is not eligible for chunk generation"
            );

        if(expectedInputHash == null
                || !expectedInputHash.equals(input.generationInputHash()))
            throw new InvalidDocumentProcessingRequestException(
                    "Document text changed after chunk generation was queued"
            );
    }

    private KnowledgeChunk createChunk(
            ChunkGenerationInput input,
            KnowledgeChunkDraft draft
    ) {
        Document document = input.document();
        DocumentPage firstPage = draft.pageContributions()
                .getFirst()
                .page();
        KnowledgeChunk chunk = new KnowledgeChunk();

        chunk.setDocument(document);
        chunk.setSourceReference(firstPage.getSourceReference());
        chunk.setChunkType(chunkType(document.getDocumentType()));
        chunk.setLanguage(isBlank(document.getLanguage())
                ? "bg"
                : document.getLanguage());
        chunk.setContent(draft.content());
        chunk.setSourceTextType(SourceTextType.CORRECTED_PAGE_TEXT);
        chunk.setChunkOrdinal(draft.ordinal());
        chunk.setContentHash(draft.contentHash());
        chunk.setGenerationInputHash(input.generationInputHash());
        chunk.setChunkingStrategy(properties.getStrategy());
        chunk.setChunkingVersion(properties.getVersion());
        chunk.setReviewState(ReviewState.APPROVED);
        chunk.setTranscriptionApprovalState(
                TranscriptionApprovalState.APPROVED
        );
        chunk.setProvenanceTrustState(
                firstPage.getProvenanceTrustState()
        );
        chunk.setIndexingState(IndexingState.PENDING);
        return chunk;
    }

    private KnowledgeChunkType chunkType(DocumentType documentType) {
        return documentType == DocumentType.STANDALONE_CAPTURE
                || documentType == DocumentType.UNKNOWN_FRAGMENT_SET
                ? KnowledgeChunkType.STANDALONE_EVIDENCE
                : KnowledgeChunkType.BOOK_EXCERPT;
    }

    private void savePageLinks(
            List<KnowledgeChunkDraft> drafts,
            List<KnowledgeChunk> chunks
    ) {
        for(int i = 0; i < drafts.size(); i++) {
            KnowledgeChunk chunk = chunks.get(i);
            List<KnowledgeChunkPage> links = drafts.get(i)
                    .pageContributions()
                    .stream()
                    .map(contribution -> toLink(chunk, contribution))
                    .toList();
            chunkPageRepository.saveAll(links);
        }
        chunkPageRepository.flush();
    }

    private KnowledgeChunkPage toLink(
            KnowledgeChunk chunk,
            ChunkPageContribution contribution
    ) {
        DocumentPage page = contribution.page();
        KnowledgeChunkPage link = new KnowledgeChunkPage();
        link.setKnowledgeChunk(chunk);
        link.setDocumentPage(page);
        link.setPageOrder(contribution.pageOrder());
        link.setStartCharOffset(contribution.startCharOffset());
        link.setEndCharOffset(contribution.endCharOffset());
        link.setStartsOnPage(contribution.startCharOffset() == 0);
        link.setEndsOnPage(
                contribution.endCharOffset() == page.getCorrectedText().length()
        );
        link.setCitationPrintedPageNumber(page.getPrintedPageNumber());
        link.setCitationPdfPageIndex(page.getPdfPageIndex());
        link.setCitationLabel(citationLabel(page));
        return link;
    }

    private String citationLabel(DocumentPage page) {
        if(!isBlank(page.getPageLabel()))
            return page.getPageLabel();

        if(!isBlank(page.getPrintedPageNumber()))
            return "p. " + page.getPrintedPageNumber();

        return page.getPdfPageIndex() == null
                ? "Page " + page.getPageSequence()
                : "PDF page " + (page.getPdfPageIndex() + 1);
    }

    private void supersede(
            List<KnowledgeChunk> previous,
            List<KnowledgeChunk> created
    ) {
        if(previous.isEmpty())
            return;

        Map<Integer, KnowledgeChunk> byOrdinal = created.stream()
                .collect(Collectors.toMap(
                        KnowledgeChunk::getChunkOrdinal,
                        Function.identity()
                ));
        KnowledgeChunk fallback = created.getFirst();

        previous.forEach(chunk -> {
            chunk.setIndexingState(IndexingState.OUTDATED);
            chunk.setSupersededBy(
                    byOrdinal.getOrDefault(chunk.getChunkOrdinal(), fallback)
            );
        });
        chunkRepository.saveAll(previous);
    }

}
