package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.*;

class DocumentChunkGenerationEligibilityServiceTest {

    @Test
    void excludesIneligiblePagesAndReturnsStableBlockers() {
        Document document = entity(new Document(), 1L);
        document.setDocumentType(DocumentType.SCANNED_BOOK);
        SourceReference source = entity(new SourceReference(), 2L);
        DocumentPage eligible = page(11L, document, source, "Одобрен текст");
        DocumentPage rejected = page(12L, document, source, "Отхвърлен текст");
        rejected.setReviewState(ReviewState.REJECTED);
        rejected.setTranscriptionApprovalState(TranscriptionApprovalState.REJECTED);
        DocumentPage retired = page(13L, document, source, "Архивиран текст");
        retired.setEvidenceState(EvidenceState.RETIRED);
        DocumentPage blank = page(14L, document, source, " ");
        DocumentPage untrusted = page(15L, document, source, "Недоверен текст");
        untrusted.setProvenanceTrustState(ProvenanceTrustState.UNTRUSTED);

        var service = service(
                document,
                List.of(eligible, rejected, retired, blank, untrusted)
        );
        var result = service.requireInput(1L);

        assertEquals(List.of(11L), result.pages().stream()
                .map(page -> page.page().getId())
                .toList());
        assertEquals(4, result.blockers().size());
        assertEquals(List.of(
                "TRANSCRIPTION_NOT_APPROVED",
                "PAGE_NOT_ACTIVE",
                "CORRECTED_TEXT_BLANK",
                "PROVENANCE_INELIGIBLE"
        ), result.blockers().stream().map(blocker -> blocker.code()).toList());
        assertEquals(64, result.generationInputHash().length());
        assertEquals(
                result.generationInputHash(),
                service.requireInput(1L).generationInputHash()
        );
    }

    private DocumentChunkGenerationEligibilityService service(
            Document document,
            List<DocumentPage> pages
    ) {
        DocumentRepository documents = proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findById"))
                        return Optional.of(document);
                    throw new AssertionError(method.getName());
                }
        );
        DocumentPageRepository pageRepository = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> {
                    if(method.getName().equals("findChunkGenerationCandidates"))
                        return pages;
                    throw new AssertionError(method.getName());
                }
        );
        DocumentChunkingProperties properties = new DocumentChunkingProperties();

        return new DocumentChunkGenerationEligibilityService(
                documents,
                pageRepository,
                new DocumentChunkGenerationInputHasher(),
                properties
        );
    }

    private DocumentPage page(
            Long id,
            Document document,
            SourceReference source,
            String text
    ) {
        DocumentPage page = entity(new DocumentPage(), id);
        page.setDocument(document);
        page.setSourceReference(source);
        page.setEvidenceState(EvidenceState.ACTIVE);
        page.setProcessingState(ProcessingState.COMPLETED);
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setProvenanceStatus(ProvenanceStatus.KNOWN_SOURCE);
        page.setProvenanceTrustState(ProvenanceTrustState.TRUSTED);
        page.setCorrectedText(text);
        page.setCorrectedTextHash(ContentHashUtils.sha256(text));
        return page;
    }

    private <T extends AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
