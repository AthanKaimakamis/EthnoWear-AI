package fmi.ethnowear.api.controller.ontology.admin;

import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionContent;
import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionDetails;
import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionRestoreCommand;
import fmi.ethnowear.application.service.ontology.version.OntologyVersionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMINISTRATOR')")
@RequestMapping("/api/admin/ontology/versions")
public class OntologyVersionController {

    private final OntologyVersionService service;

    @GetMapping
    public Page<OntologyVersionDetails> list(Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{versionId}")
    public OntologyVersionDetails get(@PathVariable Long versionId) {
        return service.get(versionId);
    }

    @GetMapping("/latest")
    public OntologyVersionDetails getLatest() {
        return service.getLatest();
    }

    @GetMapping(value = "/{versionId}/content", produces = "application/rdf+xml")
    public ResponseEntity<byte[]> download(@PathVariable Long versionId) {
        return download(service.getContent(versionId));
    }

    @GetMapping(value = "/latest/content", produces = "application/rdf+xml")
    public ResponseEntity<byte[]> downloadLatest() {
        return download(service.getLatestContent());
    }

    @PostMapping("/{versionId}/restore")
    public OntologyVersionDetails restore(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable Long versionId,
            @Valid @RequestBody OntologyVersionRestoreCommand command
    ) {
        return service.restore(versionId, command, userId(jwt));
    }

    private @NonNull Long userId(@NonNull Jwt jwt) {
        Number userId = jwt.getClaim("userId");
        if (userId == null)
            throw new IllegalArgumentException("Authenticated user id is missing");
        return userId.longValue();
    }

    private ResponseEntity<byte[]> download(OntologyVersionContent version) {
        byte[] content = version.content().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/rdf+xml;charset=UTF-8"))
                .contentLength(content.length)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(downloadFileName(version), StandardCharsets.UTF_8)
                                .build()
                                .toString()
                )
                .header("X-Content-Type-Options", "nosniff")
                .body(content);
    }

    private String downloadFileName(OntologyVersionContent version) {
        String fileName = version.fileName() == null
                ? "ontology.owx"
                : version.fileName().replace('\\', '/');
        fileName = fileName.substring(fileName.lastIndexOf('/') + 1)
                .replaceAll("[\\r\\n\\\"]", "_");

        int extensionIndex = fileName.lastIndexOf('.');
        if (extensionIndex <= 0)
            return fileName + "-v" + version.versionNumber() + ".owx";

        return fileName.substring(0, extensionIndex)
                + "-v" + version.versionNumber()
                + fileName.substring(extensionIndex);
    }
}
