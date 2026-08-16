package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetWriteDto;
import fmi.ethnowear.application.service.archive.media.MediaAssetService;
import fmi.ethnowear.application.service.archive.media.MediaUploadService;
import fmi.ethnowear.application.dto.archive.media.MediaUploadRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/media-assets")
public class AdminMediaAssetController
        extends BaseCrudController<MediaAssetWriteDto, MediaAssetDetails> {

    private final MediaUploadService uploadService;

    public AdminMediaAssetController(MediaAssetService service, MediaUploadService uploadService) {
        super(service);
        this.uploadService = uploadService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaAssetDetails> upload(
            @RequestPart("file") MultipartFile file,
            @Valid @RequestPart("metadata") MediaUploadRequest metadata) {
        return ResponseEntity.status(201).body(uploadService.upload(file, metadata));
    }
}
