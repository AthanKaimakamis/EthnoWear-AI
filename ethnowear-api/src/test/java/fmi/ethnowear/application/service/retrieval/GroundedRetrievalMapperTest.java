package fmi.ethnowear.application.service.retrieval;

import fmi.ethnowear.application.dto.retrieval.GroundedPassageDetails;
import fmi.ethnowear.application.port.retrieval.VectorSearchCandidate;
import fmi.ethnowear.domain.model.archive.KnowledgeChunkType;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.Source;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class GroundedRetrievalMapperTest {

    private final GroundedRetrievalMapper mapper = new GroundedRetrievalMapper();

    @Test
    void mapsExactPageAndSourceCitation() {
        Source source = new Source();
        EntityTestUtils.setId(source, 1L);
        source.setTitle("Български народни шевици");
        source.setAuthor("Автор");

        SourceReference reference = new SourceReference();
        EntityTestUtils.setId(reference, 2L);
        reference.setSource(source);
        reference.setChapter("Шопска област");
        reference.setPageFrom(42);

        Document document = new Document();
        EntityTestUtils.setId(document, 3L);
        document.setTitle("Сканирана книга");
        document.setDocumentType(DocumentType.PDF_DOCUMENT);
        document.setProvenanceStatus(ProvenanceStatus.KNOWN_SOURCE);

        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 4L);
        page.setDocument(document);
        page.setSourceReference(reference);
        page.setPageSequence(5);
        page.setPdfPageIndex(4);
        page.setPrintedPageNumber("42");

        KnowledgeChunk chunk = new KnowledgeChunk();
        EntityTestUtils.setId(chunk, 5L);
        chunk.setDocument(document);
        chunk.setChunkType(KnowledgeChunkType.BOOK_EXCERPT);
        chunk.setContent("Извадка");
        chunk.setLanguage("bg");
        chunk.setProvenanceTrustState(ProvenanceTrustState.VERIFIED);
        chunk.setTranscriptionApprovalState(TranscriptionApprovalState.APPROVED);

        KnowledgeChunkPage link = new KnowledgeChunkPage();
        link.setKnowledgeChunk(chunk);
        link.setDocumentPage(page);
        link.setCitationPrintedPageNumber("42");
        link.setCitationLabel("с. 42");

        GroundedPassageDetails result = mapper.toDetails(
                chunk,
                new VectorSearchCandidate(5L, 0.88, "hash"),
                List.of(link)
        );

        assertEquals(0.88, result.similarity());
        assertEquals(4L, result.pages().getFirst().pageId());
        assertEquals("42", result.pages().getFirst().printedPageNumber());
        assertEquals(2L, result.pages().getFirst().source().sourceReferenceId());
        assertEquals("Български народни шевици",
                result.pages().getFirst().source().sourceTitle());
        assertFalse(result.standaloneEvidence());
    }
}
