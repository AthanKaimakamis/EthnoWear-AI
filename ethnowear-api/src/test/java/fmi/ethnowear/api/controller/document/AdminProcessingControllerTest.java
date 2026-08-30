package fmi.ethnowear.api.controller.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.document.admin.AdminProcessingController;
import fmi.ethnowear.api.exception.DocumentApiExceptionHandler;
import fmi.ethnowear.application.dto.document.command.processing.DocumentJobCancellationCommand;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.dto.document.query.processing.*;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobLifecycleService;
import fmi.ethnowear.application.service.document.processing.DocumentWorkflowManagementService;
import fmi.ethnowear.application.service.document.query.ProcessingJobAdminQueryService;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminProcessingControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private StubQueryService queryService;
    private StubLifecycleService lifecycleService;
    private StubWorkflowManagementService workflowService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        queryService = new StubQueryService();
        lifecycleService = new StubLifecycleService();
        workflowService = new StubWorkflowManagementService();
        mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminProcessingController(
                        queryService,
                        lifecycleService,
                        workflowService
                )
                )
                .setControllerAdvice(new DocumentApiExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver()
                )
                .build();
    }

    @Test
    void listsAndCountsProcessingJobs() throws Exception {
        mockMvc.perform(get("/api/admin/processing/jobs")
                        .param("jobType", "OCR")
                        .param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(81))
                .andExpect(jsonPath("$.content[0].error.message").value("Safe failure"))
                .andExpect(jsonPath("$.content[0].errorDetailsJson").doesNotExist());

        mockMvc.perform(get("/api/admin/processing/counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.byStatus.FAILED").value(1));

        assertEquals(JobType.OCR, queryService.query.jobType());
        assertEquals(JobStatus.FAILED, queryService.query.status());
    }

    @Test
    void retriesCancelsAndBulkRetries() throws Exception {
        mockMvc.perform(post("/api/admin/processing/jobs/81/retry"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(81));

        mockMvc.perform(post("/api/admin/processing/jobs/81/cancel")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"No longer needed\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/processing/jobs/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(
                                Map.of("jobIds", List.of(81, 82))
                        )))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobs.length()").value(2));

        assertEquals(List.of(81L, 82L), lifecycleService.bulkIds);
        assertEquals("No longer needed", lifecycleService.cancellation.reason());
    }

    @Test
    void returnsStableValidationCode() throws Exception {
        mockMvc.perform(post("/api/admin/processing/jobs/retry")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobIds\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields.jobIds").exists());
    }

    @Test
    void createsReplacementAndRetiresWithVersionAndActor() throws Exception {
        mockMvc.perform(post("/api/admin/processing/jobs/81/replacement")
                        .header("If-Match", "version-token"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(91));

        mockMvc.perform(delete("/api/admin/processing/jobs/81")
                        .header("If-Match", "version-token")
                        .param("reason", "Obsolete run")
                        .principal(() -> "admin"))
                .andExpect(status().isOk());

        assertEquals(81L, workflowService.jobId);
        assertEquals("version-token", workflowService.versionToken);
        assertEquals("Obsolete run", workflowService.reason);
        assertEquals("admin", workflowService.actor);
    }

    private static ProcessingJobSummaryDetails summary() {
        return new ProcessingJobSummaryDetails(
                81L,
                JobType.OCR,
                JobStatus.FAILED,
                0,
                new ProcessingJobProgressDetails(1, 3, 2, true, false, true),
                new ProcessingJobTimingDetails(null, null, null, null, null, null, null),
                new ProcessingJobErrorDetails("OCR_FAILED", "Safe failure"),
                null,
                new ProcessingJobDocumentContextDetails(41L, "Book", "bg"),
                new ProcessingJobPageContextDetails(11L, 1, 0, "1", null),
                31L,
                null
        );
    }

    private static DocumentProcessingJobDetails history(Long id) {
        return new DocumentProcessingJobDetails(
                id, JobType.OCR, JobStatus.RETRY_WAIT, 41L, 11L, 31L,
                null, 0, 1, 3, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null
        );
    }

    private static final class StubQueryService extends ProcessingJobAdminQueryService {

        private ProcessingJobQueryDto query;

        private StubQueryService() {
            super(null, null);
        }

        @Override
        public Page<ProcessingJobSummaryDetails> findAll(
                ProcessingJobQueryDto query,
                Pageable pageable
        ) {
            this.query = query;
            return new PageImpl<>(List.of(summary()), pageable, 1);
        }

        @Override
        public ProcessingJobCountsDetails counts(ProcessingJobQueryDto query) {
            return new ProcessingJobCountsDetails(
                    1,
                    0,
                    1,
                    Map.of(JobStatus.FAILED, 1L)
            );
        }
    }

    private static final class StubLifecycleService
            extends DocumentProcessingJobLifecycleService {

        private List<Long> bulkIds;
        private DocumentJobCancellationCommand cancellation;

        private StubLifecycleService() {
            super(null, null, null, null);
        }

        @Override
        public DocumentProcessingJobDetails retry(Long jobId) {
            return history(jobId);
        }

        @Override
        public List<DocumentProcessingJobDetails> retryAll(List<Long> jobIds) {
            bulkIds = jobIds;
            return jobIds.stream().map(AdminProcessingControllerTest::history).toList();
        }

        @Override
        public DocumentProcessingJobDetails cancel(
                Long jobId,
                DocumentJobCancellationCommand command
        ) {
            cancellation = command;
            return history(jobId);
        }
    }

    private static final class StubWorkflowManagementService
            extends DocumentWorkflowManagementService {

        private Long jobId;
        private String versionToken;
        private String reason;
        private String actor;

        private StubWorkflowManagementService() {
            super(
                    null, null, null, null, null, null,
                    null, null, null, null, null, null, null
            );
        }

        @Override
        public DocumentProcessingJobDetails createReplacementJob(
                Long jobId,
                String versionToken
        ) {
            this.jobId = jobId;
            this.versionToken = versionToken;
            return history(91L);
        }

        @Override
        public DocumentProcessingJobDetails retireJob(
                Long jobId,
                String versionToken,
                String reason,
                String actor
        ) {
            this.jobId = jobId;
            this.versionToken = versionToken;
            this.reason = reason;
            this.actor = actor;
            return history(jobId);
        }
    }
}
