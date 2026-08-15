package fmi.ethnowear.api.controller.archive;

import fmi.ethnowear.application.dto.archive.query.ArchiveItemDetailDetails;
import fmi.ethnowear.application.dto.archive.query.RegionalEmbroideryArchiveOverviewDetails;
import fmi.ethnowear.application.service.archive.media.MediaDelivery;
import fmi.ethnowear.application.service.archive.media.MediaDeliveryService;
import fmi.ethnowear.application.service.archive.query.ArchiveItemDetailService;
import fmi.ethnowear.application.service.archive.query.RegionalEmbroideryArchiveService;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

import static fmi.ethnowear.util.TextUtils.isBlank;

@RestController
@RequestMapping("/api/archive")
@RequiredArgsConstructor
public class PublicArchiveController {

    private final RegionalEmbroideryArchiveService regionalEmbroideryService;
    private final ArchiveItemDetailService detailService;
    private final MediaDeliveryService mediaDeliveryService;

    @GetMapping("/regional-embroideries")
    public RegionalEmbroideryArchiveOverviewDetails findRegionalEmbroideries(
            @RequestParam(defaultValue = "bg") String language,
            @RequestParam(defaultValue = "4") int previewSize
    ) {
        return regionalEmbroideryService.findOverview(language, previewSize);
    }

    @GetMapping("/items/{id}")
    public ArchiveItemDetailDetails findItemById(@PathVariable Long id) {
        return detailService.findById(id);
    }

    @GetMapping("/media/{id}")
    public ResponseEntity<?> findMediaById(@PathVariable Long id) {
        MediaDelivery delivery = mediaDeliveryService.findById(id);

        if(delivery instanceof MediaDelivery.Redirect(java.net.URI location))
            return ResponseEntity
                    .status(302)
                    .location(location)
                    .build();

        return localMediaResponse((MediaDelivery.Local) delivery);
    }

    private @NonNull ResponseEntity<Resource> localMediaResponse(MediaDelivery.@NonNull Local delivery) {
        String fileName = delivery.fileName();

        if(isBlank(fileName))
            fileName = delivery.resource().getFilename();

        if(isBlank(fileName))
            fileName = "media";

        ContentDisposition disposition = ContentDisposition
                .inline()
                .filename(fileName, StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(contentType(delivery.mimeType()))
                .contentLength(delivery.contentLength())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        disposition.toString()
                )
                .body(delivery.resource());
    }

    private MediaType contentType(String mimeType) {
        if(isBlank(mimeType))
            return MediaType.APPLICATION_OCTET_STREAM;

        try {
            return MediaType.parseMediaType(mimeType);
        } catch (IllegalArgumentException ex) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
