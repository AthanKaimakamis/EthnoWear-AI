package fmi.ethnowear.api.controller.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentPageOcrController;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentPageProvenanceController;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentPageReviewController;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentProcessingController;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentProcessingJobController;
import fmi.ethnowear.api.exception.DocumentApiExceptionHandler;
import fmi.ethnowear.application.dto.document.command.ocr.ManualOcrImportCommand;
import fmi.ethnowear.application.dto.document.command.processing.DocumentJobCancellationCommand;
import fmi.ethnowear.application.dto.document.command.provenance.CanonicalPageLinkCommand;
import fmi.ethnowear.application.dto.document.command.provenance.PageProvenanceTrustChangeCommand;
import fmi.ethnowear.application.dto.document.command.review.CorrectedTextSaveCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageOcrResultDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageProvenanceEventDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageReviewDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.service.document.ocr.DocumentPageManualOcrImportService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobLifecycleService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingRequestService;
import fmi.ethnowear.application.service.document.provenance.DocumentPageProvenanceService;
import fmi.ethnowear.application.service.document.review.DocumentPageReviewService;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDocumentWorkflowControllersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Principal curator = () -> "curator";

    private StubManualOcrService manualOcrService;
    private StubProcessingRequestService processingRequestService;
    private StubJobLifecycleService jobLifecycleService;
    private StubProvenanceService provenanceService;
    private StubReviewService reviewService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        manualOcrService = new StubManualOcrService();
        processingRequestService = new StubProcessingRequestService();
        jobLifecycleService = new StubJobLifecycleService();
        provenanceService = new StubProvenanceService();
        reviewService = new StubReviewService();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminDocumentPageOcrController(manualOcrService),
                        new AdminDocumentProcessingController(processingRequestService),
                        new AdminDocumentProcessingJobController(jobLifecycleService),
                        new AdminDocumentPageProvenanceController(provenanceService),
                        new AdminDocumentPageReviewController(reviewService)
                )
                .setControllerAdvice(new DocumentApiExceptionHandler())
                .build();
    }

    @Test
    void importsManualOcrWithoutAdvertisingAnUnavailableResultEndpoint() throws Exception {
        ManualOcrImportCommand command = new ManualOcrImportCommand(
                31L,
                "Recognized text"
        );

        mockMvc.perform(post("/api/admin/document-pages/11/ocr-results")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(command)))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"))
                .andExpect(jsonPath("$.id").value(71));

        assertEquals(11L, manualOcrService.pageId);
        assertEquals(command, manualOcrService.command);
    }

    @Test
    void queuesEverySupportedProcessingRequest() throws Exception {
        mockMvc.perform(post("/api/admin/document-pages/11/ocr"))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("Location"));
        mockMvc.perform(post("/api/admin/document-pages/11/reprocess"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/document-pages/11/quality-assessment"))
                .andExpect(status().isAccepted());
        mockMvc.perform(post("/api/admin/documents/41/chunk-generation"))
                .andExpect(status().isAccepted());

        assertEquals(
                List.of("ocr:11", "reprocess:11", "quality:11", "chunks:41"),
                processingRequestService.calls
        );
    }

    @Test
    void exposesRetryAndValidatedCancellation() throws Exception {
        DocumentJobCancellationCommand cancellation =
                new DocumentJobCancellationCommand("No longer required");

        mockMvc.perform(post("/api/admin/document-processing-jobs/81/retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(81));
        mockMvc.perform(post("/api/admin/document-processing-jobs/81/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(cancellation)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/document-processing-jobs/81/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.reason").exists());

        assertEquals(81L, jobLifecycleService.retriedJobId);
        assertEquals(81L, jobLifecycleService.cancelledJobId);
        assertEquals(cancellation, jobLifecycleService.cancellation);
    }

    @Test
    void obtainsReviewerFromAuthenticatedPrincipal() throws Exception {
        CorrectedTextSaveCommand command =
                new CorrectedTextSaveCommand("Corrected text");

        mockMvc.perform(patch("/api/admin/document-pages/11/transcription")
                        .principal(curator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(command)))
                .andExpect(status().isOk());

        assertEquals(11L, reviewService.pageId);
        assertEquals(command, reviewService.command);
        assertEquals("curator", reviewService.reviewer);
    }

    @Test
    void exposesTypedProvenanceCommandsWithAuthenticatedReviewer() throws Exception {
        PageProvenanceTrustChangeCommand command =
                new PageProvenanceTrustChangeCommand(
                        ProvenanceTrustState.TRUSTED,
                        "Source reviewed"
                );

        mockMvc.perform(post(
                                "/api/admin/document-pages/11/provenance-events/trust-change"
                        )
                        .principal(curator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(91));

        assertEquals(11L, provenanceService.pageId);
        assertEquals(command, provenanceService.command);
        assertEquals("curator", provenanceService.reviewer);
    }

    @Test
    void rejectsCanonicalLinkWithoutTargetPage() throws Exception {
        CanonicalPageLinkCommand invalid = new CanonicalPageLinkCommand(
                null,
                "Duplicate evidence"
        );

        mockMvc.perform(post(
                                "/api/admin/document-pages/11/provenance-events/canonical-link"
                        )
                        .principal(curator)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.canonicalDocumentPageId").exists());
    }

    private static DocumentPageOcrResultDetails ocrResult() {
        return new DocumentPageOcrResultDetails(
                71L, 11L, 31L, null, "Recognized text",
                "MANUAL_IMPORT", null, "bg", null, true, null, null
        );
    }

    private static DocumentProcessingJobDetails job(Long id) {
        return new DocumentProcessingJobDetails(
                id, JobType.OCR, JobStatus.QUEUED, 41L, 11L, 31L,
                null, 0, 0, 3, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );
    }

    private static DocumentPageProvenanceEventDetails provenanceEvent() {
        return new DocumentPageProvenanceEventDetails(
                91L, 11L, null, null, null, null, null, null,
                ProvenanceTrustState.TRUSTED, null, null, "curator",
                "Source reviewed", null
        );
    }

    private static final class StubManualOcrService
            extends DocumentPageManualOcrImportService {

        private Long pageId;
        private ManualOcrImportCommand command;

        private StubManualOcrService() {
            super(null, null);
        }

        @Override
        public DocumentPageOcrResultDetails importResult(
                Long pageId,
                ManualOcrImportCommand command
        ) {
            this.pageId = pageId;
            this.command = command;
            return ocrResult();
        }
    }

    private static final class StubProcessingRequestService
            extends DocumentProcessingRequestService {

        private final List<String> calls = new ArrayList<>();

        private StubProcessingRequestService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public DocumentProcessingJobDetails requestOcr(Long pageId) {
            calls.add("ocr:" + pageId);
            return job(81L);
        }

        @Override
        public DocumentProcessingJobDetails requestReprocessing(Long pageId) {
            calls.add("reprocess:" + pageId);
            return job(82L);
        }

        @Override
        public DocumentProcessingJobDetails requestQualityAssessment(Long pageId) {
            calls.add("quality:" + pageId);
            return job(83L);
        }

        @Override
        public DocumentProcessingJobDetails requestChunkGeneration(Long documentId) {
            calls.add("chunks:" + documentId);
            return job(84L);
        }
    }

    private static final class StubJobLifecycleService
            extends DocumentProcessingJobLifecycleService {

        private Long retriedJobId;
        private Long cancelledJobId;
        private DocumentJobCancellationCommand cancellation;

        private StubJobLifecycleService() {
            super(null, null, null);
        }

        @Override
        public DocumentProcessingJobDetails retry(Long jobId) {
            retriedJobId = jobId;
            return job(jobId);
        }

        @Override
        public DocumentProcessingJobDetails cancel(
                Long jobId,
                DocumentJobCancellationCommand command
        ) {
            cancelledJobId = jobId;
            cancellation = command;
            return job(jobId);
        }
    }

    private static final class StubProvenanceService
            extends DocumentPageProvenanceService {

        private Long pageId;
        private PageProvenanceTrustChangeCommand command;
        private String reviewer;

        private StubProvenanceService() {
            super(null, null, null, null);
        }

        @Override
        public DocumentPageProvenanceEventDetails changeTrust(
                Long pageId,
                PageProvenanceTrustChangeCommand command,
                String reviewer
        ) {
            this.pageId = pageId;
            this.command = command;
            this.reviewer = reviewer;
            return provenanceEvent();
        }
    }

    private static final class StubReviewService
            extends DocumentPageReviewService {

        private Long pageId;
        private CorrectedTextSaveCommand command;
        private String reviewer;

        private StubReviewService() {
            super(null, null, null, null);
        }

        @Override
        public DocumentPageReviewDetails saveCorrectedText(
                Long pageId,
                CorrectedTextSaveCommand command,
                String reviewer
        ) {
            this.pageId = pageId;
            this.command = command;
            this.reviewer = reviewer;
            return null;
        }
    }
}
