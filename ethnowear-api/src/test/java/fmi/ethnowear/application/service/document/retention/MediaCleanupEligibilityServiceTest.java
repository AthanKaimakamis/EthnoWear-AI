package fmi.ethnowear.application.service.document.retention;

import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaCleanupEligibilityServiceTest {

    @Test
    void cleanupBecomesEligibleOnlyAfterDocumentAggregateIsIndexed() {
        Document document = new Document();
        EntityTestUtils.setId(document, 1L);
        document.setProcessingState(ProcessingState.COMPLETED);
        document.setIndexingState(IndexingState.PENDING);

        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, 11L);
        page.setDocument(document);
        page.setProcessingState(ProcessingState.COMPLETED);
        page.setTranscriptionApprovalState(
                TranscriptionApprovalState.APPROVED
        );

        DocumentRepository documents = mock(DocumentRepository.class);
        when(documents.findById(1L)).thenReturn(Optional.of(document));

        DocumentPageRepository pages = mock(DocumentPageRepository.class);
        when(pages.findByDocument_IdOrderByPageSequenceAsc(1L))
                .thenReturn(List.of(page));

        KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
        when(chunks.countCurrentEligibleByDocumentId(1L)).thenReturn(1L);
        when(chunks.countCurrentEligibleByDocumentIdAndIndexingStateNot(
                1L,
                IndexingState.INDEXED
        )).thenReturn(0L);

        DocumentProcessingJobRepository jobs = mock(
                DocumentProcessingJobRepository.class
        );
        when(jobs.existsActiveDocumentJobOtherThan(anyLong(), any()))
                .thenReturn(false);

        DocumentPageMediaRepository media = mock(
                DocumentPageMediaRepository.class
        );
        when(media.findCleanupCandidates(anyLong(), anySet(), any(), isNull()))
                .thenReturn(List.of(new DocumentPageMedia()));

        MediaCleanupEligibilityService service = new MediaCleanupEligibilityService(
                documents,
                pages,
                media,
                jobs,
                chunks,
                new GeneratedDocumentMediaPolicy()
        );

        assertFalse(service.evaluate(1L).eligible());

        document.setIndexingState(IndexingState.INDEXED);

        assertTrue(service.evaluate(1L).eligible());
    }
}
