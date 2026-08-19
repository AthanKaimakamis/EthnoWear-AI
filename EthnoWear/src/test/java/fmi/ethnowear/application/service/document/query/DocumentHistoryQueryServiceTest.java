package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static fmi.ethnowear.support.RepositoryTestProxies.rejecting;
import static org.junit.jupiter.api.Assertions.*;

class DocumentHistoryQueryServiceTest {

    @Test
    void enforcesRepositoryHistoryOrderAndRemovesClientSorting() {
        DocumentQueryGuard guard = pageGuard();
        DocumentPageOcrResult result = new DocumentPageOcrResult();
        EntityTestUtils.setId(result, 9L);
        result.setRawText("history");

        DocumentPageOcrResultRepository repository = proxy(
                DocumentPageOcrResultRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals(
                            "findByDocumentPage_IdOrderByCreatedAtDescIdDesc",
                            method.getName()
                    );
                    assertEquals(5L, arguments[0]);
                    var pageable = (PageRequest) arguments[1];
                    assertTrue(pageable.getSort().isUnsorted());
                    return new PageImpl<>(List.of(result), pageable, 1);
                }
        );

        DocumentHistoryQueryService service = new DocumentHistoryQueryService(
                guard,
                repository,
                rejecting(DocumentPageReviewRepository.class),
                rejecting(DocumentPageProvenanceEventRepository.class),
                rejecting(DocumentProcessingJobRepository.class),
                new DocumentHistoryMapper()
        );

        var page = service.findOcrHistory(
                3L,
                5L,
                PageRequest.of(0, 20, Sort.by("rawText"))
        );

        assertEquals(List.of(9L), page.map(value -> value.id()).getContent());
    }

    @Test
    void rejectsOversizedHistoryPage() {
        DocumentHistoryQueryService service = new DocumentHistoryQueryService(
                pageGuard(),
                rejecting(DocumentPageOcrResultRepository.class),
                rejecting(DocumentPageReviewRepository.class),
                rejecting(DocumentPageProvenanceEventRepository.class),
                rejecting(DocumentProcessingJobRepository.class),
                new DocumentHistoryMapper()
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> service.findOcrHistory(
                        3L,
                        5L,
                        PageRequest.of(0, 101)
                )
        );
    }

    private DocumentQueryGuard pageGuard() {
        return new DocumentQueryGuard(null, null) {
            @Override
            public void requirePage(Long documentId, Long pageId) {
                assertEquals(3L, documentId);
                assertEquals(5L, pageId);
            }
        };
    }
}
