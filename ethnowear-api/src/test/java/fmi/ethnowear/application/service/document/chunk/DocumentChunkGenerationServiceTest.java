package fmi.ethnowear.application.service.document.chunk;

import fmi.ethnowear.application.model.document.chunk.ChunkGenerationInput;
import fmi.ethnowear.application.model.document.chunk.EligibleChunkPage;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.config.DocumentChunkingProperties;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import fmi.ethnowear.persistence.jpa.entity.KnowledgeChunk;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.KnowledgeChunkPage;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.KnowledgeChunkPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.ContentHashUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DocumentChunkGenerationServiceTest {

    @Test
    void approvedPageWithoutChunksCreatesInitialGeneration() {
        Fixture fixture = fixture();
        AtomicLong ids = new AtomicLong(70L);

        when(fixture.chunkPages().findCurrentByDocumentPageId(11L))
                .thenReturn(List.of());
        when(fixture.chunks().saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<KnowledgeChunk> chunks = invocation.getArgument(0);
            chunks.forEach(chunk -> entity(chunk, ids.getAndIncrement()));
            return chunks;
        });

        assertTrue(fixture.service().generatePageIfRequired(1L, 11L));

        verify(fixture.scheduler()).queueIndexChunk(any(KnowledgeChunk.class));
        verify(fixture.chunkPages()).saveAll(any());
    }

    @Test
    void identicalInputReturnsExistingGenerationWithoutWriting() {
        Fixture fixture = fixture();
        KnowledgeChunk existing = entity(new KnowledgeChunk(), 51L);
        when(fixture.chunks()
                .findByDocument_IdAndGenerationInputHashOrderByChunkOrdinalAsc(
                        1L,
                        fixture.input().generationInputHash()
                )).thenReturn(List.of(existing));

        var result = fixture.service().generate(
                1L,
                fixture.input().generationInputHash()
        );

        assertTrue(result.existingGeneration());
        assertEquals(List.of(existing), result.chunks());
        verify(fixture.chunks(), never()).saveAllAndFlush(any());
        verifyNoInteractions(fixture.scheduler());
    }

    @Test
    void changedInputCreatesCitationsAndSupersedesPreviousSet() {
        Fixture fixture = fixture();
        KnowledgeChunk previous = entity(new KnowledgeChunk(), 41L);
        previous.setChunkOrdinal(0);
        previous.setIndexingState(IndexingState.INDEXED);
        AtomicLong ids = new AtomicLong(60L);
        AtomicReference<List<KnowledgeChunkPage>> savedLinks =
                new AtomicReference<>(List.of());

        when(fixture.chunks()
                .findByDocument_IdAndGenerationInputHashOrderByChunkOrdinalAsc(
                        1L,
                        fixture.input().generationInputHash()
                )).thenReturn(List.of());
        when(fixture.chunks()
                .findByDocument_IdAndGenerationInputHashIsNotNullAndSupersededByIsNullOrderByChunkOrdinalAsc(
                        1L
                )).thenReturn(List.of(previous));
        when(fixture.chunks().saveAllAndFlush(any())).thenAnswer(invocation -> {
            List<KnowledgeChunk> chunks = invocation.getArgument(0);
            chunks.forEach(chunk -> entity(chunk, ids.getAndIncrement()));
            return chunks;
        });
        when(fixture.chunkPages().saveAll(any())).thenAnswer(invocation -> {
            Iterable<KnowledgeChunkPage> values = invocation.getArgument(0);
            List<KnowledgeChunkPage> links = new ArrayList<>();
            values.forEach(links::add);
            savedLinks.set(links);
            return links;
        });
        var result = fixture.service().generate(
                1L,
                fixture.input().generationInputHash()
        );

        assertFalse(result.existingGeneration());
        assertEquals(1, result.chunks().size());
        assertSame(result.chunks().getFirst(), previous.getSupersededBy());
        assertEquals(IndexingState.OUTDATED, previous.getIndexingState());
        assertEquals(IndexingState.PENDING, result.chunks().getFirst().getIndexingState());
        assertEquals(1, savedLinks.get().size());
        KnowledgeChunkPage citation = savedLinks.get().getFirst();
        assertEquals(11L, citation.getDocumentPage().getId());
        assertEquals(0, citation.getStartCharOffset());
        assertEquals(fixture.page().getCorrectedText().length(), citation.getEndCharOffset());
        assertEquals("15", citation.getCitationPrintedPageNumber());
        assertEquals(3, citation.getCitationPdfPageIndex());
        verify(fixture.scheduler()).queueIndexChunk(result.chunks().getFirst());
    }

    private Fixture fixture() {
        Document document = entity(new Document(), 1L);
        document.setDocumentType(DocumentType.SCANNED_BOOK);
        document.setLanguage("bg");
        SourceReference source = entity(new SourceReference(), 2L);
        DocumentPage page = entity(new DocumentPage(), 11L);
        page.setDocument(document);
        page.setSourceReference(source);
        page.setCorrectedText("Одобрен текст за знание.");
        page.setCorrectedTextHash(ContentHashUtils.sha256(page.getCorrectedText()));
        page.setProvenanceTrustState(ProvenanceTrustState.TRUSTED);
        page.setPrintedPageNumber("15");
        page.setPdfPageIndex(3);
        page.setPageSequence(4);
        String inputHash = "a".repeat(64);
        ChunkGenerationInput input = new ChunkGenerationInput(
                document,
                inputHash,
                List.of(new EligibleChunkPage(
                        page,
                        page.getCorrectedText(),
                        page.getCorrectedTextHash()
                )),
                List.of()
        );
        DocumentChunkGenerationEligibilityService eligibility = mock(
                DocumentChunkGenerationEligibilityService.class
        );
        when(eligibility.requireInput(1L)).thenReturn(input);
        when(eligibility.requirePageInput(1L, 11L)).thenReturn(input);
        DocumentChunkingProperties properties = new DocumentChunkingProperties();
        KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
        KnowledgeChunkPageRepository chunkPages = mock(
                KnowledgeChunkPageRepository.class
        );
        DocumentProcessingJobScheduler scheduler = mock(
                DocumentProcessingJobScheduler.class
        );

        DocumentChunkGenerationService service = new DocumentChunkGenerationService(
                eligibility,
                new DeterministicPageAwareChunker(properties),
                chunks,
                chunkPages,
                scheduler,
                properties,
                indexingStateReconciler(document)
        );

        return new Fixture(
                service,
                input,
                page,
                chunks,
                chunkPages,
                scheduler
        );
    }

    private fmi.ethnowear.application.service.document.indexing.DocumentIndexingStateReconciler indexingStateReconciler(
            Document document
    ) {
        var reconciler = mock(
                fmi.ethnowear.application.service.document.indexing.DocumentIndexingStateReconciler.class
        );
        when(reconciler.lockDocument(document.getId())).thenReturn(document);
        return reconciler;
    }

    private <T extends AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }

    private record Fixture(
            DocumentChunkGenerationService service,
            ChunkGenerationInput input,
            DocumentPage page,
            KnowledgeChunkRepository chunks,
            KnowledgeChunkPageRepository chunkPages,
            DocumentProcessingJobScheduler scheduler
    ) {
    }
}
