package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.archive.media.MediaAssetWriteDto;
import fmi.ethnowear.application.service.archive.media.MediaAssetService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/media-assets")
public class AdminMediaAssetController
        extends BaseCrudController<MediaAssetWriteDto, MediaAssetDetails> {

    public AdminMediaAssetController(MediaAssetService service) {
        super(service);
    }
}
