package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.upload.*;
import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.service.document.upload.DocumentThumbnailService;
import fmi.ethnowear.application.service.document.upload.MissingPageUploadService;
import fmi.ethnowear.application.service.document.upload.PdfDocumentUploadService;
import fmi.ethnowear.application.service.document.upload.ReplacementRenditionUploadService;
import fmi.ethnowear.application.service.document.upload.StandaloneCaptureUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/admin/documents")
@RequiredArgsConstructor
public class AdminDocumentUploadController {

    private final PdfDocumentUploadService pdfUploadService;
    private final StandaloneCaptureUploadService captureUploadService;
    private final MissingPageUploadService missingPageUploadService;
    private final ReplacementRenditionUploadService replacementUploadService;
    private final DocumentThumbnailService thumbnailService;

    @PostMapping(
            path = "/upload/pdf",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentUploadDetails> uploadPdf(
            @Valid @RequestPart("command")
            PdfDocumentUploadCommand command,
            @RequestPart("file")
            MultipartFile file,
            @RequestPart(value = "thumbnail", required = false)
            MultipartFile thumbnail
    ) {
        DocumentUploadDetails result = pdfUploadService.upload(command, file, thumbnail);

        return ResponseEntity
                .created(documentLocation(result.documentId()))
                .body(result);
    }

    @PostMapping(
            path = "/upload/standalone-capture",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentUploadDetails> uploadStandaloneCapture(
            @Valid @RequestPart("command")
            StandaloneCaptureUploadCommand command,
            @RequestPart("file")
            MultipartFile file,
            @RequestPart(value = "thumbnail", required = false)
            MultipartFile thumbnail
    ) {
        DocumentUploadDetails result = captureUploadService.upload(command, file, thumbnail);

        return ResponseEntity
                .created(documentLocation(result.documentId()))
                .body(result);
    }

    @PutMapping(
            path = "/{documentId}/thumbnail",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public MediaAssetDetails uploadThumbnail(
            @PathVariable Long documentId,
            @RequestPart("file") MultipartFile file
    ) {
        return thumbnailService.upload(documentId, file);
    }

    @PostMapping(
            path = "/{documentId}/pages/missing",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentUploadDetails> uploadMissingPage(
            @PathVariable Long documentId,
            @Valid @RequestPart("command")
            MissingPageUploadCommand command,
            @RequestPart("file")
            MultipartFile file
    ) {
        DocumentUploadDetails result = missingPageUploadService.upload(documentId, command, file);

        return ResponseEntity
                .created(pageLocation(result.documentId(), result.documentPageId()))
                .body(result);
    }

    @PostMapping(
            value = "/{documentId}/pages/{pageId}/renditions/replacement",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<DocumentUploadDetails> uploadReplacementRendition(
            @PathVariable Long documentId,
            @PathVariable Long pageId,
            @Valid @RequestPart("command")
            ReplacementRenditionUploadCommand command,
            @RequestPart("file")
            MultipartFile file
    ) {
        DocumentUploadDetails result = replacementUploadService
                .upload(documentId, pageId, command, file);

        return ResponseEntity
                .created(pageLocation(result.documentId(), result.documentPageId()))
                .body(result);
    }

    private @NonNull URI documentLocation(Long documentId) {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/admin/documents/{documentId}")
                .buildAndExpand(documentId)
                .toUri();
    }

    private @NonNull URI pageLocation(Long documentId, Long pageId) {
        return ServletUriComponentsBuilder
                .fromCurrentContextPath()
                .path("/api/admin/documents/{documentId}/pages/{pageId}")
                .buildAndExpand(documentId, pageId)
                .toUri();
    }
}
