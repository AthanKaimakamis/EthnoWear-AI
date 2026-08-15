package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemWriteDto;
import fmi.ethnowear.application.service.archive.item.ArchiveItemService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/archive-items")
public class AdminArchiveItemController
        extends BaseCrudController<ArchiveItemWriteDto, ArchiveItemDetails> {

    public AdminArchiveItemController(ArchiveItemService archiveItemService) {
        super(archiveItemService);
    }
}
