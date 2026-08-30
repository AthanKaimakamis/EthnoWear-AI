package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.service.document.query.mapper.DocumentQualityMapper;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPageQualitySignalProjection;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualitySignalRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentQualityQueryServiceTest {

    @Test
    void loadsCurrentAssessmentsAndSignalsInSeparateBatches() {
        DocumentQueryGuard guard = new DocumentQueryGuard(null, null) {
            @Override
            public void requirePage(Long documentId, Long pageId) {
                assertEquals(3L, documentId);
                assertEquals(5L, pageId);
            }
        };

        DocumentPageQualityAssessment assessment =
                new DocumentPageQualityAssessment();
        EntityTestUtils.setId(assessment, 10L);

        DocumentPageQualityAssessmentRepository assessmentRepository = proxy(
                DocumentPageQualityAssessmentRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals(
                            "findByDocumentPage_IdAndCurrentTrueOrderByAssessmentTypeAscIdAsc",
                            method.getName()
                    );
                    assertEquals(5L, arguments[0]);
                    return List.of(assessment);
                }
        );

        DocumentPageQualitySignalProjection signal = proxy(
                DocumentPageQualitySignalProjection.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getId" -> 20L;
                    case "getAssessmentId" -> 10L;
                    case "getSignalOrdinal" -> 1;
                    default -> null;
                }
        );

        DocumentPageQualitySignalRepository signalRepository = proxy(
                DocumentPageQualitySignalRepository.class,
                (ignored, method, arguments) -> {
                    assertEquals("findSafeSignals", method.getName());
                    assertEquals(List.of(10L), arguments[0]);
                    return List.of(signal);
                }
        );

        DocumentQualityQueryService service = new DocumentQualityQueryService(
                guard,
                assessmentRepository,
                signalRepository,
                new DocumentQualityMapper()
        );

        var result = service.findCurrentAssessments(3L, 5L);

        assertEquals(10L, result.getFirst().id());
        assertEquals(20L, result.getFirst().signals().getFirst().id());
    }
}
