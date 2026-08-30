package fmi.ethnowear.api.controller.document;

import fmi.ethnowear.api.controller.document.admin.AdminDocumentController;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentPageController;
import fmi.ethnowear.api.exception.DocumentApiExceptionHandler;
import fmi.ethnowear.application.dto.document.query.*;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageOcrResultDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentPageProvenanceEventDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.query.*;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.processing.ProcessingState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDocumentQueryControllersTest {

    private StubDocumentQueryService documentService;
    private StubDocumentHistoryQueryService historyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        documentService = new StubDocumentQueryService();
        historyService = new StubDocumentHistoryQueryService();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminDocumentController(
                                documentService,
                                null,
                                null,
                                historyService,
                                null,
                                null
                        ),
                new AdminDocumentPageController(
                        null,
                        historyService,
                        null,
                        null,
                        null
                )
                )
                .setControllerAdvice(new DocumentApiExceptionHandler())
                .setCustomArgumentResolvers(
                        new PageableHandlerMethodArgumentResolver()
                )
                .build();
    }

    @Test
    void bindsDocumentFiltersAndForwardsPagination() throws Exception {
        documentService.result = new PageImpl<>(
                List.of(summary(41L)),
                PageRequest.of(2, 7),
                20
        );

        mockMvc.perform(get("/api/admin/documents")
                        .queryParam("searchText", "embroidery")
                        .queryParam("documentType", "SCANNED_BOOK")
                        .queryParam("processingState", "COMPLETED")
                        .queryParam("language", "bg")
                        .queryParam("sourceId", "9")
                        .queryParam("page", "2")
                        .queryParam("size", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(41));

        assertEquals("embroidery", documentService.query.searchText());
        assertEquals(
                DocumentType.SCANNED_BOOK,
                documentService.query.documentType()
        );
        assertEquals(
                ProcessingState.COMPLETED,
                documentService.query.processingState()
        );
        assertEquals("bg", documentService.query.language());
        assertEquals(9L, documentService.query.sourceId());
        assertEquals(2, documentService.pageable.getPageNumber());
        assertEquals(7, documentService.pageable.getPageSize());
    }

    @Test
    void exposesCorrectProvenanceAndJobRoutes() throws Exception {
        mockMvc.perform(get(
                        "/api/admin/documents/5/pages/11/provenance"
                ))
                .andExpect(status().isOk());

        assertEquals(5L, historyService.documentId);
        assertEquals(11L, historyService.pageId);
        assertEquals("provenance", historyService.lastCall);

        mockMvc.perform(get(
                        "/api/admin/documents/5/pages/11/jobs"
                ))
                .andExpect(status().isOk());

        assertEquals("page-jobs", historyService.lastCall);
    }

    @Test
    void returnsNotFoundWhenCurrentOcrResultDoesNotExist() throws Exception {
        historyService.currentOcrResult = Optional.empty();

        mockMvc.perform(get(
                        "/api/admin/documents/5/pages/11/ocr/current"
                ))
                .andExpect(status().isNotFound());
    }

    @Test
    void mapsMissingDocumentToDocumentApiError() throws Exception {
        documentService.failure = new ResourceNotFoundException(
                "Document",
                404L
        );

        mockMvc.perform(get("/api/admin/documents/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message")
                        .value("Document not found: 404"));
    }

    private DocumentSummaryDetails summary(Long id) {
        return new DocumentSummaryDetails(
                id,
                null,
                null,
                null,
                null,
                null,
                DocumentType.SCANNED_BOOK,
                null,
                "Document",
                null,
                null,
                null,
                "bg",
                10,
                ProcessingState.COMPLETED,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private static final class StubDocumentQueryService
            extends DocumentQueryService {

        private Page<DocumentSummaryDetails> result = Page.empty();
        private DocumentQueryDto query;
        private Pageable pageable;
        private RuntimeException failure;

        private StubDocumentQueryService() {
            super(null, null, null, null, null);
        }

        @Override
        public Page<DocumentSummaryDetails> findAll(
                DocumentQueryDto query,
                Pageable pageable
        ) {
            this.query = query;
            this.pageable = pageable;
            return result;
        }

        @Override
        public DocumentDetails findById(Long documentId) {
            if(failure != null)
                throw failure;

            return null;
        }
    }

    private static final class StubDocumentHistoryQueryService
            extends DocumentHistoryQueryService {

        private Long documentId;
        private Long pageId;
        private String lastCall;
        private Optional<DocumentPageOcrResultDetails> currentOcrResult =
                Optional.empty();

        private StubDocumentHistoryQueryService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public Optional<DocumentPageOcrResultDetails> findCurrentOcrResult(
                Long documentId,
                Long pageId
        ) {
            this.documentId = documentId;
            this.pageId = pageId;
            this.lastCall = "current-ocr";
            return currentOcrResult;
        }

        @Override
        public Page<DocumentPageProvenanceEventDetails> findProvenanceHistory(
                Long documentId,
                Long pageId,
                Pageable pageable
        ) {
            this.documentId = documentId;
            this.pageId = pageId;
            this.lastCall = "provenance";
            return emptyPage();
        }

        @Override
        public Page<DocumentProcessingJobDetails> findPageJobs(
                Long documentId,
                Long pageId,
                Pageable pageable
        ) {
            this.documentId = documentId;
            this.pageId = pageId;
            this.lastCall = "page-jobs";
            return emptyPage();
        }

        private <T> Page<T> emptyPage() {
            return new PageImpl<>(
                    List.of(),
                    PageRequest.of(0, 20),
                    0
            );
        }
    }
}
