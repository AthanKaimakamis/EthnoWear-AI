package fmi.ethnowear.application.dto.document.query;

import fmi.ethnowear.application.dto.document.query.history.*;
import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualityAssessmentDetails;
import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualitySignalDetails;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentQueryContractTest {

    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            "filePath",
            "thumbnailPath",
            "checksum",
            "errorDetailsJson",
            "activeJobKey",
            "rowVersion",
            "vectorPointId",
            "vectorCollection",
            "embeddingModel",
            "parametersJson",
            "structuredOutputJson"
    );

    @Test
    void outwardDocumentContractsExcludeInternalFields() {
        List<Class<?>> contracts = List.of(
                DocumentSummaryDetails.class,
                DocumentDetails.class,
                DocumentProgressDetails.class,
                DocumentProgressSummaryDetails.class,
                DocumentPageSummaryDetails.class,
                DocumentPageDetails.class,
                DocumentPageMediaDetails.class,
                DocumentSourceDetails.class,
                DocumentIndexingStatusDetails.class,
                DocumentPageOcrResultDetails.class,
                DocumentPageReviewDetails.class,
                DocumentPageProvenanceEventDetails.class,
                DocumentProcessingJobDetails.class,
                DocumentPageQualityAssessmentDetails.class,
                DocumentPageQualitySignalDetails.class
        );

        contracts.forEach(contract -> {
            Set<String> fields = Arrays.stream(contract.getRecordComponents())
                    .map(RecordComponent::getName)
                    .collect(Collectors.toSet());

            assertTrue(
                    fields.stream().noneMatch(FORBIDDEN_FIELDS::contains),
                    () -> contract.getSimpleName() + " exposes an internal field"
            );
        });
    }
}
