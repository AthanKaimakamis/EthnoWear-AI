package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetMetadataWriteDto;
import fmi.ethnowear.application.service.archive.media.asset.MediaAssetService;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/media-assets")
public class AdminMediaAssetController {

    private final MediaAssetService service;
    private final MediaUploadService uploadService;

    @GetMapping
    public Page<MediaAssetDetails> findAll(Pageable pageable) {
        return service.findAll(pageable);
    }

    @GetMapping("/{id}")
    public MediaAssetDetails findById(@PathVariable Long id) {
        return service.findById(id);
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
}
