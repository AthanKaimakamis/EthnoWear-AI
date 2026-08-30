package fmi.ethnowear.application.dto.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.dto.worker.extraction.PageExtractionManifestDetails;
import fmi.ethnowear.application.dto.worker.extraction.PageExtractionManifestPageDetails;
import fmi.ethnowear.application.dto.worker.failure.WorkerJobFailureDetails;
import fmi.ethnowear.application.dto.worker.heartbeat.WorkerHeartbeatDetails;
import fmi.ethnowear.application.dto.worker.job.*;
import fmi.ethnowear.application.dto.worker.ocr.WorkerOcrResultDetails;
import fmi.ethnowear.application.dto.worker.rendition.WorkerPageRenditionDetails;
import fmi.ethnowear.application.dto.worker.rendition.WorkerPageRenditionType;
import fmi.ethnowear.application.dto.worker.quality.WorkerQualityAssessmentContextDetails;
import fmi.ethnowear.application.dto.worker.quality.WorkerQualityAssessmentDetails;
import fmi.ethnowear.application.dto.worker.vision.WorkerVisionAssessmentCommand;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WorkerContractDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void responsesDoNotExposeStorageOrPersistenceInternals() throws Exception {
        List<Object> responses = List.of(
                new WorkerJobClaimDetails(
                        11L,
                        WorkerJobType.PAGE_EXTRACTION,
                        "opaque-claim-token",
                        1,
                        Instant.parse("2026-08-21T08:00:00Z"),
                        Instant.parse("2026-08-21T08:02:00Z"),
                        new WorkerJobTargetDetails(7L, null, null, true),
                        new WorkerResourceLimitsDetails(
                                100,
                                10,
                                300,
                                1000,
                                1000,
                                1000000,
                                100,
                                2000000,
                                10000000,
                                1800,
                                30,
                                300
                        )
                ),
                new WorkerHeartbeatDetails(Instant.parse("2026-08-21T08:02:00Z"), false),
                new PageExtractionManifestDetails(
                        7L,
                        1,
                        List.of(new PageExtractionManifestPageDetails(21L, 0, 1, true))
                ),
                new WorkerPageRenditionDetails(
                        21L,
                        41L,
                        31L,
                        WorkerPageRenditionType.PDF_PAGE_RENDER,
                        false
                ),
                new WorkerOcrResultDetails(51L, 11L, 7L, 21L, 31L, false),
                new WorkerQualityAssessmentContextDetails(
                        12L,
                        7L,
                        21L,
                        51L,
                        41L,
                        31L,
                        "OCR text",
                        new BigDecimal("0.9000"),
                        "bul",
                        "{\"words\":[]}",
                        "image/png",
                        1024L,
                        1200,
                        1800,
                        300,
                        "RGB"
                ),
                new WorkerQualityAssessmentDetails(61L, 12L, 7L, 21L, 51L, 31L, false),
                new WorkerJobCompletionDetails(
                        11L,
                        WorkerJobType.PAGE_EXTRACTION,
                        7L,
                        null,
                        1,
                        1,
                        0,
                        false
                ),
                new WorkerJobFailureDetails(11L, JobStatus.RETRY_WAIT, Instant.now(), false)
        );

        for(Object response : responses) {
            String json = objectMapper.writeValueAsString(response).toLowerCase();
            assertFalse(json.contains("filepath"));
            assertFalse(json.contains("storagekey"));
            assertFalse(json.contains("activejobkey"));
            assertFalse(json.contains("claimtokenhash"));
            assertFalse(json.contains("errordetailsjson"));
            assertFalse(json.contains("rowversion"));
        }
    }

    @Test
    void acceptsCurrentVisionWorkerAssessmentPayload() throws Exception {
        String payload = """
                {
                  "assessment": {
                    "assessorName": "gemma3:4b",
                    "assessorVersion": "sha256:model",
                    "scoreVersion": "vision-ocr-v3",
                    "overallScore": 0.82,
                    "qualityStatus": "WARNING",
                    "summary": "Vision comparison requires human review",
                    "limitations": "Advisory result only",
                    "signals": [{
                      "type": "VISION_ISSUES_REJECTED",
                      "textValue": "{\\\"count\\\":1,\\\"stages\\\":[\\\"issue_excerpt\\\"]}",
                      "severity": "WARNING",
                      "weight": 1.0,
                      "safeMessage": "Invalid vision guidance was discarded"
                    }]
                  },
                  "requiresReview": true,
                  "suggestedText": "Raw OCR text",
                  "modelName": "gemma3:4b",
                  "modelVersion": "sha256:model",
                  "promptVersion": "vision-ocr-v3",
                  "issues": [{
                    "issueType": "OCR_WORD",
                    "explanationBg": "Разпознатата дума е неточна",
                    "confidence": 0.95,
                    "originalText": "OCR",
                    "originalContext": "Raw OCR text",
                    "suggestedText": "ОСР",
                    "suggestedContext": "Raw ОСР text",
                    "startOffset": 4,
                    "endOffset": 7,
                    "safelyApplicable": true
                  }],
                  "uncertainPassages": []
                }
                """;

        WorkerVisionAssessmentCommand command = objectMapper.readValue(
                payload,
                WorkerVisionAssessmentCommand.class
        );

        assertEquals("OCR_WORD", command.issues().getFirst().issueType());
        assertEquals("Raw OCR text", command.issues().getFirst().originalContext());
        assertTrue(command.issues().getFirst().safelyApplicable());
    }
}
