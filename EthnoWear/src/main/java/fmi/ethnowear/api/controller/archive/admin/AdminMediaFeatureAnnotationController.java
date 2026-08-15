package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.api.dto.archive.media.MediaFeatureAnnotationDetails;
import fmi.ethnowear.api.dto.archive.media.MediaFeatureAnnotationWriteDto;
import fmi.ethnowear.application.service.archive.media.MediaFeatureAnnotationService;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/admin/media-feature-annotations")
public class AdminMediaFeatureAnnotationController
        extends BaseCrudController<MediaFeatureAnnotationWriteDto, MediaFeatureAnnotationDetails> {

    public AdminMediaFeatureAnnotationController(MediaFeatureAnnotationService service) {
        super(service);
    }
}
