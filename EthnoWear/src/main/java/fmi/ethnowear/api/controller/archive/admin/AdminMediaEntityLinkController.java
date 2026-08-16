package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.application.dto.archive.media.*;
import fmi.ethnowear.application.service.archive.media.MediaEntityLinkService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/media-entity-links")
public class AdminMediaEntityLinkController extends BaseCrudController<MediaEntityLinkWriteDto, MediaEntityLinkDetails> {
    public AdminMediaEntityLinkController(MediaEntityLinkService service) { super(service); }
}
