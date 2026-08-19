package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPageQualitySignalProjection;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static fmi.ethnowear.support.RepositoryTestProxies.proxy;

class DocumentQualityMapperTest {

    @Test
    void groupsSignalsUnderTheirAssessment() {
        DocumentPageQualityAssessment first = assessment(10L);
        DocumentPageQualityAssessment second = assessment(20L);

        DocumentPageQualitySignalProjection signal = proxy(
                DocumentPageQualitySignalProjection.class,
                (ignored, method, arguments) -> switch (method.getName()) {
                    case "getId" -> 100L;
                    case "getAssessmentId" -> 20L;
                    case "getSignalType" -> "OCR_CONFIDENCE";
                    case "getSignalOrdinal" -> 1;
                    default -> null;
                }
        );

        var result = new DocumentQualityMapper().toDetails(
                List.of(first, second),
                List.of(signal)
        );

        assertEquals(List.of(), result.getFirst().signals());
        assertEquals(List.of(100L), result.getLast().signals()
                .stream()
                .map(value -> value.id())
                .toList());
    }

    private DocumentPageQualityAssessment assessment(Long id) {
        DocumentPageQualityAssessment assessment =
                new DocumentPageQualityAssessment();
        EntityTestUtils.setId(assessment, id);
        return assessment;
    }
}
