package fmi.ethnowear.application.dto.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.application.dto.worker.completion.WorkerJobCompletionDetails;
import fmi.ethnowear.application.dto.worker.extraction.PageExtractionManifestDetails;
import fmi.ethnowear.application.dto.worker.extraction.PageExtractionManifestPageDetails;
import fmi.ethnowear.application.dto.worker.failure.WorkerJobFailureDetails;
import fmi.ethnowear.application.dto.worker.heartbeat.WorkerHeartbeatDetails;
import fmi.ethnowear.application.dto.worker.job.*;
import fmi.ethnowear.application.dto.worker.rendition.WorkerPageRenditionDetails;
import fmi.ethnowear.application.dto.worker.rendition.WorkerPageRenditionType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;

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
                new WorkerJobCompletionDetails(11L, 7L, 1, 1, false),
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
}
