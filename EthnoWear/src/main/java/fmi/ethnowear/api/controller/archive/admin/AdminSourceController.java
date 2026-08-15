package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.api.dto.archive.source.SourceDetails;
import fmi.ethnowear.api.dto.archive.source.SourceWriteDto;
import fmi.ethnowear.application.service.archive.source.SourceService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/sources")
public class AdminSourceController
        extends BaseCrudController<SourceWriteDto, SourceDetails> {

    public AdminSourceController(SourceService service) {
        super(service);
    }
}
