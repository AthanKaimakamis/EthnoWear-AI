package fmi.ethnowear.api.controller;

import fmi.ethnowear.ontology.admin.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    public OntologyEntityDetails create(@PathVariable("entityType") String entityType,
                                        @RequestBody OntologyEntityCommand command) {
        return service.create(kind(entityType), command);
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
