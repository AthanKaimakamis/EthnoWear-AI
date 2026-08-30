package fmi.ethnowear.api.controller.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentController;
import fmi.ethnowear.api.controller.document.admin.AdminDocumentUploadController;
import fmi.ethnowear.api.exception.DocumentApiExceptionHandler;
import fmi.ethnowear.application.dto.document.command.metadata.DocumentMetadataUpdateCommand;
import fmi.ethnowear.application.dto.document.command.upload.*;
import fmi.ethnowear.application.service.document.metadata.DocumentMetadataService;
import fmi.ethnowear.application.service.document.upload.MissingPageUploadService;
import fmi.ethnowear.application.service.document.upload.PdfDocumentUploadService;
import fmi.ethnowear.domain.model.document.DocumentType;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceStatus;
import fmi.ethnowear.domain.model.document.provenance.ProvenanceTrustState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminDocumentUploadControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StubPdfUploadService pdfUploadService;
    private StubMissingPageUploadService missingPageUploadService;
    private StubDocumentMetadataService metadataService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        pdfUploadService = new StubPdfUploadService();
        missingPageUploadService = new StubMissingPageUploadService();
        metadataService = new StubDocumentMetadataService();

        mockMvc = MockMvcBuilders.standaloneSetup(
                        new AdminDocumentUploadController(
                                pdfUploadService,
                                null,
                                missingPageUploadService,
                                null,
                                null
                        ),
                        new AdminDocumentController(
                                null,
                                null,
                                null,
                                null,
                                metadataService,
                                null
                        )
                )
                .setControllerAdvice(new DocumentApiExceptionHandler())
                .build();
    }

    @Test
    void uploadsPdfAndReturnsDocumentLocation() throws Exception {
        PdfDocumentUploadCommand command = new PdfDocumentUploadCommand(
                metadataInput(),
                DocumentType.PDF_DOCUMENT,
                ProvenanceStatus.UNKNOWN_SOURCE,
                ProvenanceTrustState.UNKNOWN,
                "Original scan"
        );
        MockMultipartFile commandPart = jsonPart(command);
        MockMultipartFile filePart = new MockMultipartFile(
                "file",
                "book.pdf",
                MediaType.APPLICATION_PDF_VALUE,
                "%PDF-1.7".getBytes()
        );
        pdfUploadService.result = new DocumentUploadDetails(
                41L,
                51L,
                null,
                null,
                61L
        );

        mockMvc.perform(multipart("/api/admin/documents/upload/pdf")
                        .file(commandPart)
                        .file(filePart))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/admin/documents/41"
                ))
                .andExpect(jsonPath("$.documentId").value(41))
                .andExpect(jsonPath("$.processingJobId").value(61));

        assertEquals(command, pdfUploadService.command);
        assertSame(filePart, pdfUploadService.file);
    }

    @Test
    void uploadsMissingPageAndReturnsPageLocation() throws Exception {
        MissingPageUploadCommand command = new MissingPageUploadCommand(
                7,
                unknownProvenance(),
                null,
                null,
                "Missing page",
                false,
                null,
                null
        );
        missingPageUploadService.result = new DocumentUploadDetails(
                41L,
                52L,
                71L,
                81L,
                null
        );

        mockMvc.perform(multipart(
                                "/api/admin/documents/41/pages/missing"
                        )
                        .file(jsonPart(command))
                        .file(imagePart()))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "Location",
                        "http://localhost/api/admin/documents/41/pages/71"
                ))
                .andExpect(jsonPath("$.documentPageId").value(71));

        assertEquals(41L, missingPageUploadService.documentId);
        assertEquals(command, missingPageUploadService.command);
    }

    @Test
    void updatesCuratorEditableDocumentMetadata() throws Exception {
        DocumentMetadataUpdateCommand command =
                new DocumentMetadataUpdateCommand(
                        "Updated title",
                        "Author",
                        "Publisher",
                        1934,
                        "bg",
                        "Curator notes"
                );

        mockMvc.perform(put("/api/admin/documents/41/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(command)))
                .andExpect(status().isNoContent());

        assertEquals(41L, metadataService.documentId);
        assertEquals(command, metadataService.command);
    }

    @Test
    void rejectsInvalidDocumentMetadata() throws Exception {
        mockMvc.perform(put("/api/admin/documents/41/metadata")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value("Validation failed"))
                .andExpect(jsonPath("$.fields.title").exists());
    }

    private MockMultipartFile jsonPart(Object command) throws Exception {
        return new MockMultipartFile(
                "command",
                "command.json",
                MediaType.APPLICATION_JSON_VALUE,
                objectMapper.writeValueAsBytes(command)
        );
    }

    private MockMultipartFile imagePart() {
        return new MockMultipartFile(
                "file",
                "page.png",
                MediaType.IMAGE_PNG_VALUE,
                new byte[]{1, 2, 3}
        );
    }

    private DocumentBibliographicInput metadataInput() {
        return new DocumentBibliographicInput(
                null,
                null,
                "Document title",
                null,
                null,
                null,
                "bg",
                null
        );
    }

    private PageProvenanceInput unknownProvenance() {
        return new PageProvenanceInput(
                null,
                ProvenanceStatus.UNKNOWN_SOURCE,
                ProvenanceTrustState.UNKNOWN,
                null,
                "curator",
                "Source is currently unknown"
        );
    }

    private static final class StubPdfUploadService
            extends PdfDocumentUploadService {

        private PdfDocumentUploadCommand command;
        private MultipartFile file;
        private DocumentUploadDetails result;

        private StubPdfUploadService() {
            super(null, null, null, null, null, null, null, null, null);
        }

        @Override
        public DocumentUploadDetails upload(
                PdfDocumentUploadCommand command,
                MultipartFile file
        ) {
            this.command = command;
            this.file = file;
            return result;
        }

        @Override
        public DocumentUploadDetails upload(
                PdfDocumentUploadCommand command,
                MultipartFile file,
                MultipartFile thumbnail
        ) {
            return upload(command, file);
        }
    }

    private static final class StubMissingPageUploadService
            extends MissingPageUploadService {

        private Long documentId;
        private MissingPageUploadCommand command;
        private DocumentUploadDetails result;

        private StubMissingPageUploadService() {
            super(
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        @Override
        public DocumentUploadDetails upload(
                Long documentId,
                MissingPageUploadCommand command,
                MultipartFile file
        ) {
            this.documentId = documentId;
            this.command = command;
            return result;
        }
    }

    private static final class StubDocumentMetadataService
            extends DocumentMetadataService {

        private Long documentId;
        private DocumentMetadataUpdateCommand command;

        private StubDocumentMetadataService() {
            super(null, null);
        }

        @Override
        public void update(
                Long documentId,
                DocumentMetadataUpdateCommand command
        ) {
            this.documentId = documentId;
            this.command = command;
        }
    }
}
