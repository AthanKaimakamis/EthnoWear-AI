package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.DocumentMediaLinkDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetMetadataWriteDto;
import fmi.ethnowear.application.service.archive.media.asset.MediaAssetService;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDelivery;
import fmi.ethnowear.application.service.archive.media.delivery.MediaDeliveryService;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.service.archive.media.asset.DocumentMediaLinkQueryService;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/media-assets")
public class AdminMediaAssetController {

    private final MediaAssetService service;
    private final MediaUploadService uploadService;
    private final DocumentMediaLinkQueryService documentLinkQueryService;
    private final MediaDeliveryService deliveryService;

    @GetMapping
    public Page<MediaAssetDetails> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    public MediaAssetDetails findById(@PathVariable Long id) {
        return service.findById(id);
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<?> content(@PathVariable Long id) {
        MediaDelivery delivery = deliveryService.findById(id);
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

    @GetMapping("/document-links")
    public List<DocumentMediaLinkDetails> findDocumentLinks(
            @RequestParam List<Long> mediaAssetIds
    ) {
        return documentLinkQueryService.findByMediaAssetIds(mediaAssetIds);
    }

    @PatchMapping("/{id}")
    public MediaAssetDetails updateMetadata(@PathVariable Long id,
                                            @Valid @RequestBody MediaAssetMetadataWriteDto request) {
        return service.updateMetadata(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaAssetDetails> upload(@RequestPart("file") MultipartFile file,
                                                    @Valid @RequestPart("metadata") MediaUploadRequest metadata) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(uploadService.upload(file, metadata));
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

        return fileName == null || fileName.isBlank() ? "media" : fileName;
    }
}
