package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.DocumentProgressDetails;
import fmi.ethnowear.application.dto.document.query.DocumentQueryDto;
import fmi.ethnowear.application.service.document.query.mapper.DocumentProgressMapper;
import fmi.ethnowear.application.service.document.query.mapper.DocumentQueryMapper;
import fmi.ethnowear.domain.model.document.indexing.IndexingState;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import fmi.ethnowear.domain.model.document.review.ReviewState;
import fmi.ethnowear.domain.model.document.review.TranscriptionApprovalState;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentQueryServiceTest {

    @Test
    void forwardsTrimmedFiltersAndBatchesProgress() {
        Document document = new Document();
        EntityTestUtils.setId(document, 7L);
        document.setTitle("Test document");

        PageRequest pageable = PageRequest.of(
                0,
                20,
                Sort.by(Sort.Direction.DESC, "title")
        );

        DocumentRepository repository = proxy(
                DocumentRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals("findDocuments", method.getName());
                    assertEquals("needle", arguments[0]);
                    assertEquals("bg", arguments[7]);
                    assertEquals(3L, arguments[8]);
                    assertEquals(pageable, arguments[9]);
                    return new PageImpl<>(List.of(document), pageable, 1);
                }
        );

        DocumentProgressDetails progress = emptyProgress();
        DocumentProgressQueryService progressService =
                new DocumentProgressQueryService(null, null, null) {
                    @Override
                    public Map<Long, DocumentProgressDetails> findByDocumentIds(
                            Collection<Long> documentIds
                    ) {
                        assertEquals(List.of(7L), List.copyOf(documentIds));
                        return Map.of(7L, progress);
                    }
                };

        DocumentQueryService service = service(repository, progressService);

        var result = service.findAll(
                new DocumentQueryDto(
                        "  needle  ", null, null, null, null,
                        null, null, " bg ", 3L
                ),
                pageable
        );

        assertEquals(List.of(7L), result.map(value -> value.id()).getContent());
        assertEquals("Test document", result.getContent().getFirst().title());
    }

    @Test
    void rejectsUnsupportedSortBeforeRepositoryQuery() {
        DocumentQueryService service = service(
                rejecting(DocumentRepository.class),
                null
        );

        DocumentQueryDto query = new DocumentQueryDto(
                null, null, null, null, null,
                null, null, null, null
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findAll(
                        query,
                        PageRequest.of(0, 20, Sort.by("filePath"))
                )
        );
    }

    private DocumentQueryService service(
            DocumentRepository repository,
            DocumentProgressQueryService progressService
    ) {
        return new DocumentQueryService(
                repository,
                progressService,
                null,
                null,
                new DocumentQueryMapper(new DocumentProgressMapper())
        );
    }

    private DocumentProgressDetails emptyProgress() {
        return new DocumentProgressDetails(
                0,
                zeroCounts(ProcessingState.class),
                zeroCounts(ReviewState.class),
                zeroCounts(TranscriptionApprovalState.class),
                zeroCounts(IndexingState.class)
        );
    }

    private <E extends Enum<E>> Map<E, Long> zeroCounts(Class<E> type) {
        Map<E, Long> counts = new EnumMap<>(type);
        for (E value : type.getEnumConstants())
            counts.put(value, 0L);
        return counts;
    }
}
