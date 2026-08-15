package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.api.dto.archive.item.ArchiveItemFeatureDetails;
import fmi.ethnowear.api.dto.archive.item.ArchiveItemFeatureWriteDto;
import fmi.ethnowear.application.service.archive.item.ArchiveItemFeatureService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/archive-item-features")
public class AdminArchiveItemFeatureController
        extends BaseCrudController<ArchiveItemFeatureWriteDto, ArchiveItemFeatureDetails> {

    public AdminArchiveItemFeatureController(ArchiveItemFeatureService service) {
        super(service);
    }
}
