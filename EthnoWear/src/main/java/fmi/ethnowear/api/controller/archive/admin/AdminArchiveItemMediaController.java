package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaWriteDto;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/admin/archive-item-media")
public class AdminArchiveItemMediaController
        extends BaseCrudController<ArchiveItemMediaWriteDto, ArchiveItemMediaDetails> {

    public AdminArchiveItemMediaController(ArchiveItemMediaService service) {
        super(service);
    }
}
