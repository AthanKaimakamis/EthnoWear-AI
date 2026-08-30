package fmi.ethnowear.application.service.retrieval;

import fmi.ethnowear.application.port.retrieval.VectorSearchCandidate;
import fmi.ethnowear.config.WorkerIndexingProperties;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.EvidenceState;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrievalCandidateValidatorTest {

    private static final String CONTENT = "Одобрен текст за българска шевица.";
    private static final String HASH = ContentHashUtils.sha256(CONTENT);

    private final RetrievalCandidateValidator validator =
            new RetrievalCandidateValidator(new WorkerIndexingProperties(
                    100_000,
                    4_096,
                    100,
                    150,
                    255,
                    "bge-m3",
                    1_024,
                    "ethnowear_chunks_bge_m3_v1"
            ));

    @Test
    void acceptsCurrentApprovedIndexedCandidate() {
        Fixture fixture = fixture();

        assertTrue(validator.isEligible(
                fixture.chunk(),
                new VectorSearchCandidate(10L, 0.91, HASH),
                List.of(fixture.link())
        ));
    }

    @Test
    void rejectsMissingEmbeddingDimensionsWithoutThrowing() {
        Fixture fixture = fixture();
        fixture.chunk().setEmbeddingDimensions(null);

        assertFalse(validator.isEligible(
                fixture.chunk(),
                new VectorSearchCandidate(10L, 0.91, HASH),
                List.of(fixture.link())
        ));
    }

    private Fixture fixture() {
        SourceReference reference = new SourceReference();
        EntityTestUtils.setId(reference, 20L);

        Document document = new Document();
        EntityTestUtils.setId(document, 30L);
        document.setDocumentType(DocumentType.PDF_DOCUMENT);

        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 40L);
        page.setDocument(document);
        page.setSourceReference(reference);
        page.setEvidenceState(EvidenceState.ACTIVE);
        page.setProcessingState(ProcessingState.COMPLETED);
        page.setReviewState(ReviewState.APPROVED);
        page.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        page.setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        page.setIndexingState(IndexingState.INDEXED);
        page.setCorrectedText(CONTENT);
        page.setCorrectedTextHash(HASH);

        KnowledgeChunk chunk = new KnowledgeChunk();
        EntityTestUtils.setId(chunk, 10L);
        chunk.setDocument(document);
        chunk.setSourceReference(reference);
        chunk.setChunkType(KnowledgeChunkType.BOOK_EXCERPT);
        chunk.setContent(CONTENT);
        chunk.setContentHash(HASH);
        chunk.setIndexedContentHash(HASH);
        chunk.setIndexingState(IndexingState.INDEXED);
        chunk.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);
        chunk.setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        chunk.setEmbeddingModel("bge-m3");
        chunk.setEmbeddingDimensions(1_024);
        chunk.setVectorCollection("ethnowear_chunks_bge_m3_v1");
        chunk.setVectorPointId("10");

        KnowledgeChunkPage link = new KnowledgeChunkPage();
        link.setKnowledgeChunk(chunk);
        link.setDocumentPage(page);

        return new Fixture(chunk, link);
    }

    private record Fixture(
            KnowledgeChunk chunk,
            KnowledgeChunkPage link
    ) {
    }
}
