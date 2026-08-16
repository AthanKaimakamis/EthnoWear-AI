package fmi.ethnowear.api.controller.archive;

import fmi.ethnowear.application.service.archive.media.MediaDelivery;
import fmi.ethnowear.application.service.archive.media.MediaDeliveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/media")
@RequiredArgsConstructor
public class MediaController {
    private final MediaDeliveryService service;

    @GetMapping("/{mediaId}/content")
    public ResponseEntity<?> content(@PathVariable Long mediaId) {
        MediaDelivery delivery = service.findById(mediaId);
        if (delivery instanceof MediaDelivery.Redirect(java.net.URI uri))
            return ResponseEntity.status(HttpStatus.FOUND).location(uri).build();
        MediaDelivery.Local local = (MediaDelivery.Local) delivery;
        MediaType type;
        try { type = MediaType.parseMediaType(local.mimeType()); }
        catch (RuntimeException ex) { type = MediaType.APPLICATION_OCTET_STREAM; }
        String name = local.fileName() == null ? "media" : local.fileName();
        return ResponseEntity.ok().contentType(type).contentLength(local.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build().toString())
                .body(local.resource());
    }
}
