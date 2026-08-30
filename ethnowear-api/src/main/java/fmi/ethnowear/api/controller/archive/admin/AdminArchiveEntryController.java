package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryDetails;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryWriteDto;
import fmi.ethnowear.application.service.archive.workflow.ArchiveEntryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static fmi.ethnowear.api.util.ResponseUtil.created;

@RestController
@RequestMapping("/api/admin/archive-entries")
@RequiredArgsConstructor
public class AdminArchiveEntryController {

    private final ArchiveEntryService archiveEntryService;

    @GetMapping("/{archiveItemId}")
    public ArchiveEntryDetails findById(@PathVariable Long archiveItemId) {
        return archiveEntryService.findById(archiveItemId);
    }

    @PostMapping
    public ResponseEntity<ArchiveEntryDetails> create(@Valid @RequestBody ArchiveEntryWriteDto request) {
        ArchiveEntryDetails result = archiveEntryService.create(request);
        return created(result, result.archiveItem().id());
    }

    @PutMapping("/{archiveItemId}")
    public ArchiveEntryDetails update(
            @PathVariable Long archiveItemId,
            @Valid @RequestBody ArchiveEntryWriteDto request
    ) {
        return archiveEntryService.update(archiveItemId, request);
    }
}
