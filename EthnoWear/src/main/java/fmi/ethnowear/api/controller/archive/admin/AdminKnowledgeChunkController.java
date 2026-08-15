package fmi.ethnowear.api.controller.archive.admin;

import fmi.ethnowear.api.controller.BaseCrudController;
import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkDetails;
import fmi.ethnowear.application.dto.archive.knowledge.KnowledgeChunkWriteDto;
import fmi.ethnowear.application.service.archive.knowledge.KnowledgeChunkService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/knowledge-chunks")
public class AdminKnowledgeChunkController
        extends BaseCrudController<KnowledgeChunkWriteDto, KnowledgeChunkDetails> {

    public AdminKnowledgeChunkController(KnowledgeChunkService service) {
        super(service);
    }
}
