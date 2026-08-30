package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.service.document.query.mapper.DocumentPageQueryMapper;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPagePreviewMediaProjection;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentPageQueryServiceTest {

    @Test
    void preservesRepositoryPageOrderAndLoadsPreviewsInOneBatch() {
        DocumentQueryGuard guard = new DocumentQueryGuard(null, null) {
            @Override
            public void requireDocument(Long documentId) {
                assertEquals(7L, documentId);
            }
        };

        Document document = new Document();
        EntityTestUtils.setId(document, 7L);
        DocumentPage first = page(11L, 1, document);
        DocumentPage second = page(12L, 2, document);
        PageRequest pageable = PageRequest.of(0, 20);

        DocumentPageRepository pageRepository = proxy(
                DocumentPageRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals(
                            "findDocumentPages",
                            method.getName()
                    );
                    assertEquals(7L, arguments[0]);
                    assertEquals(pageable, arguments[4]);
                    return new PageImpl<>(
                            List.of(first, second),
                            pageable,
                            2
                    );
                }
        );

        DocumentPagePreviewMediaProjection preview = proxy(
                DocumentPagePreviewMediaProjection.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getDocumentPageId" -> 11L;
                    case "getMediaAssetId" -> 50L;
                    case "getDisplayOrder" -> 0;
                    case "isPreferredOcrInput" -> false;
                    case "getRenditionType" -> null;
                    default -> throw new AssertionError(method.getName());
                }
        );

        DocumentPageMediaRepository mediaRepository = proxy(
                DocumentPageMediaRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals("findPreviewCandidates", method.getName());
                    assertEquals(List.of(11L, 12L), arguments[0]);
                    return List.of(preview);
                }
        );

        DocumentPageQueryService service = new DocumentPageQueryService(
                guard,
                pageRepository,
                mediaRepository,
                new DocumentPageQueryMapper()
        );

        var result = service.findByDocumentId(7L, pageable);

        assertEquals(List.of(11L, 12L), result.map(value -> value.id()).getContent());
        assertEquals(50L, result.getContent().getFirst().previewMediaAssetId());
    }

    private DocumentPage page(
            Long id,
            int sequence,
            Document document
    ) {
        DocumentPage page = new DocumentPage();
        EntityTestUtils.setId(page, id);
        page.setDocument(document);
        page.setPageSequence(sequence);
        return page;
    }
}
