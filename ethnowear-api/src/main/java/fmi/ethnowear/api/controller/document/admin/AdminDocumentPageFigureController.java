package fmi.ethnowear.api.controller.document.admin;

import fmi.ethnowear.application.dto.document.command.figure.FigureCaptionUpdateCommand;
import fmi.ethnowear.application.dto.document.command.figure.FigureReviewCommand;
import fmi.ethnowear.application.dto.document.query.figure.DocumentPageFigureDetails;
import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.application.service.document.figure.DocumentPageFigureAdminService;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDelivery;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDeliveryService;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/admin/document-pages/{pageId}/figures")
@RequiredArgsConstructor
public class AdminDocumentPageFigureController {

    private final DocumentPageFigureAdminService service;
    private final MediaDeliveryService mediaDeliveryService;

    @GetMapping
    public List<DocumentPageFigureDetails> findAll(@PathVariable Long pageId) {
        return service.findAll(pageId);
    }

    @GetMapping("/{figureId}")
    public DocumentPageFigureDetails findById(
            @PathVariable Long pageId,
            @PathVariable Long figureId
    ) {
        return service.findById(pageId, figureId);
    }

    @Operation(summary = "Preview a figure crop during authenticated human review")
    @GetMapping("/{figureId}/content")
    public ResponseEntity<?> content(
            @PathVariable Long pageId,
            @PathVariable Long figureId
    ) {
        DocumentPageFigureDetails figure = service.findById(pageId, figureId);
        MediaDelivery delivery = mediaDeliveryService.findById(figure.mediaAssetId());

        if (delivery instanceof MediaDelivery.Redirect(java.net.URI location))
            return ResponseEntity.status(HttpStatus.FOUND).location(location).build();

        MediaDelivery.Local local = (MediaDelivery.Local) delivery;
        return ResponseEntity.ok()
                .contentType(contentType(local.mimeType()))
                .contentLength(local.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(fileName(local), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .body(local.resource());
    }

    @PatchMapping("/{figureId}")
    public DocumentPageFigureDetails update(
            @PathVariable Long pageId,
            @PathVariable Long figureId,
            @RequestHeader("If-Match") String version,
            @Valid @RequestBody FigureCaptionUpdateCommand command
    ) {
        return service.update(pageId, figureId, version, command);
    }

    @PostMapping("/{figureId}/approve")
    public DocumentPageFigureDetails approve(
            @PathVariable Long pageId,
            @PathVariable Long figureId,
            @RequestHeader("If-Match") String version,
            @Valid @RequestBody FigureReviewCommand command,
            Principal principal
    ) {
        return service.review(
                pageId,
                figureId,
                version,
                command,
                FigureReviewState.APPROVED,
                principal.getName()
        );
    }

    @PostMapping("/{figureId}/reject")
    public DocumentPageFigureDetails reject(
            @PathVariable Long pageId,
            @PathVariable Long figureId,
            @RequestHeader("If-Match") String version,
            @Valid @RequestBody FigureReviewCommand command,
            Principal principal
    ) {
        return service.review(
                pageId,
                figureId,
                version,
                command,
                FigureReviewState.REJECTED,
                principal.getName()
        );
    }

    @DeleteMapping("/{figureId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long pageId,
            @PathVariable Long figureId,
            @RequestHeader("If-Match") String version
    ) {
        service.delete(pageId, figureId, version);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Queue a fresh extraction attempt for current page figure candidates")
    @PostMapping("/reextract")
    public ResponseEntity<DocumentProcessingJobDetails> reextract(
            @PathVariable Long pageId
    ) {
        return ResponseEntity.accepted().body(service.reextract(pageId));
    }

    private MediaType contentType(String mimeType) {
        try {
            return MediaType.parseMediaType(mimeType);
        } catch (RuntimeException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }

    private String fileName(MediaDelivery.Local local) {
        String fileName = local.fileName();
        if (fileName == null || fileName.isBlank())
            fileName = local.resource().getFilename();

        return fileName == null || fileName.isBlank() ? "figure" : fileName;
    }
}
