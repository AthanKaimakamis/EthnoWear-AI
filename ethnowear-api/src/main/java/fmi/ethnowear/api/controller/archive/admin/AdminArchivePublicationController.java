package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.workflow.ArchivePublicationReadinessDetails;
import fmi.ethnowear.application.service.archive.workflow.ArchivePublicationService;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/archive-items/{archiveItemId}")
@RequiredArgsConstructor
public class AdminArchivePublicationController {

    private final ArchivePublicationService publicationService;

    @GetMapping("/publication-readiness")
    public ArchivePublicationReadinessDetails validate(@PathVariable("archiveItemId") Long archiveItemId) {
        return publicationService.validate(archiveItemId);
    }

    @PostMapping("/submit")
    public ArchiveItemDetails submit(@PathVariable("archiveItemId") Long archiveItemId) {
        return publicationService.submit(archiveItemId);
    }

    @PostMapping("/publish")
    public ArchiveItemDetails publish(@PathVariable("archiveItemId") Long archiveItemId) {
        return publicationService.publish(archiveItemId);
    }

    @PostMapping("/return-to-draft")
    public ArchiveItemDetails returnToDraft(@PathVariable("archiveItemId") Long archiveItemId) {
        return publicationService.returnToDraft(archiveItemId);
    }

    @PostMapping("/archive")
    public ArchiveItemDetails archive(@PathVariable("archiveItemId") Long archiveItemId) {
        return publicationService.archive(archiveItemId);
    }
}
