package fmi.ethnowear.api.controller.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentPageMetadataController;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentSourceReferenceController;
import fmi.ethnowear.application.dto.document.command.metadata.DocumentPageMetadataUpdateCommand;
import fmi.ethnowear.application.dto.document.command.provenance.DocumentDefaultSourceReferenceCommand;
import fmi.ethnowear.application.dto.document.query.DocumentSourceInheritanceDetails;
import fmi.ethnowear.application.service.document.metadata.DocumentPageMetadataService;
import fmi.ethnowear.application.service.document.provenance.DocumentSourceReferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDocumentMetadataControllersTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void updatesPrintedPageMetadataWithOptimisticConcurrency() throws Exception {
        DocumentPageMetadataService service = mock(
                DocumentPageMetadataService.class
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminDocumentPageMetadataController(service)
        ).build();
        DocumentPageMetadataUpdateCommand command =
                new DocumentPageMetadataUpdateCommand("xv", 15, "Preface");

        mockMvc.perform(patch(
                                "/api/admin/documents/7/pages/11/metadata"
                        )
                        .header("If-Match", "AQIDBAUGBwg")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(command)))
                .andExpect(status().isNoContent());

        verify(service).update(7L, 11L, "AQIDBAUGBwg", command);
    }

    @Test
    void setsDefaultReferenceAndRequestsBulkInheritance() throws Exception {
        DocumentSourceReferenceService service = mock(
                DocumentSourceReferenceService.class
        );
        DocumentDefaultSourceReferenceCommand command =
                new DocumentDefaultSourceReferenceCommand(
                        5L,
                        true,
                        "Use the scanned book citation"
                );
        when(service.setDefault(7L, command, "curator")).thenReturn(
                new DocumentSourceInheritanceDetails(7L, 5L, 12)
        );
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(
                new AdminDocumentSourceReferenceController(service)
        ).build();

        mockMvc.perform(put(
                                "/api/admin/documents/7/default-source-reference"
                        )
                        .principal(() -> "curator")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(command)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inheritedPageCount").value(12));

        verify(service).setDefault(7L, command, "curator");
    }
}
