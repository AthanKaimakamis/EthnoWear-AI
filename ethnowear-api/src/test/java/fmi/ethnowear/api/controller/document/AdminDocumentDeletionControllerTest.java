package fmi.ethnowear.api.controller.document;

import fmi.ethnowear.api.controller.document.admin.AdminDocumentDeletionController;
import fmi.ethnowear.api.exception.DocumentApiExceptionHandler;
import fmi.ethnowear.application.service.document.lifecycle.DocumentDeletionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class AdminDocumentDeletionControllerTest {

    private DocumentDeletionService service;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        service = mock(DocumentDeletionService.class);
        mockMvc = standaloneSetup(
                new AdminDocumentDeletionController(service)
        )
                .setControllerAdvice(new DocumentApiExceptionHandler())
                .build();
    }

    @Test
    void deletesConfirmedDocument() throws Exception {
        mockMvc.perform(delete("/api/admin/documents/7")
                        .queryParam("confirm", "true"))
                .andExpect(status().isNoContent());

        verify(service).delete(7L, true);
    }
}
