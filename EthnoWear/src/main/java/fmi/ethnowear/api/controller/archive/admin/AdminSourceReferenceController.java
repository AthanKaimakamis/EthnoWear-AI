package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.application.dto.archive.source.SourceReferenceDetails;
import fmi.ethnowear.application.dto.archive.source.SourceReferenceWriteDto;
import fmi.ethnowear.application.service.archive.source.SourceReferenceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/source-references")
public class AdminSourceReferenceController
        extends BaseCrudController<SourceReferenceWriteDto, SourceReferenceDetails> {

    public AdminSourceReferenceController(SourceReferenceService service) {
        super(service);
    }
}
