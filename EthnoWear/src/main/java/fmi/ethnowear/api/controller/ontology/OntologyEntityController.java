package fmi.ethnowear.api.controller.ontology;

import fmi.ethnowear.application.enums.OntologyEntityKind;
import fmi.ethnowear.ontology.admin.*;
import fmi.ethnowear.ontology.admin.command.OntologyEntityCommand;
import fmi.ethnowear.ontology.admin.model.OntologyEntityDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static fmi.ethnowear.api.util.ResponseUtil.created;

@RestController
@RequestMapping("/api/admin/ontology")
public class OntologyEntityController {
    private final OntologyEntityAdminService service;

    public OntologyEntityController(OntologyEntityAdminService service) {
        this.service = service;
    }

    @GetMapping("/{entityType:regions|motifs|regional-embroideries}")
    public List<OntologyEntityDetails> list(@PathVariable("entityType") String entityType) {
        return service.list(kind(entityType));
    }

    @GetMapping("/{entityType:regions|motifs|regional-embroideries}/{localName}")
    public OntologyEntityDetails get(@PathVariable("entityType") String entityType,
                                     @PathVariable("localName") String localName) {
        return service.get(kind(entityType), localName);
    }

    @PostMapping("/{entityType:regions|motifs|regional-embroideries}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<OntologyEntityDetails> create(@PathVariable("entityType") String entityType,
                                                        @RequestBody OntologyEntityCommand command) {
        OntologyEntityDetails result = service.create(kind(entityType), command);
        return created(result, result.localName());
    }

    @PutMapping("/{entityType:regions|motifs|regional-embroideries}/{localName}")
    public OntologyEntityDetails update(@PathVariable("entityType") String entityType,
                                        @PathVariable("localName") String localName,
                                        @RequestBody OntologyEntityCommand command) {
        return service.update(kind(entityType), localName, command);
    }

    @DeleteMapping("/{entityType:regions|motifs|regional-embroideries}/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("entityType") String entityType,
                       @PathVariable("localName") String localName) {
        service.delete(kind(entityType), localName);
    }

    private OntologyEntityKind kind(String entityType) {
        return switch (entityType) {
            case "regions" -> OntologyEntityKind.REGION;
            case "motifs" -> OntologyEntityKind.MOTIF;
            case "regional-embroideries" -> OntologyEntityKind.REGIONAL_EMBROIDERY;
            default -> throw new IllegalArgumentException("Unsupported entity type: " + entityType);
        };
    }
}
