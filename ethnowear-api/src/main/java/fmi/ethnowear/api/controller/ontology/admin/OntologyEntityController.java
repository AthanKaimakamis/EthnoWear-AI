package fmi.ethnowear.api.controller.ontology.admin;

import fmi.ethnowear.domain.model.ontology.OntologyEntityKind;
import fmi.ethnowear.application.port.ontology.admin.OntologyEntityAdminPort;
import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityCommand;
import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityDetails;
import fmi.ethnowear.application.dto.ontology.admin.RegionDerivedTypeSynchronizationDetails;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static fmi.ethnowear.api.util.ResponseUtil.created;

@RestController
@RequestMapping("/api/admin/ontology")
public class OntologyEntityController {
    private final OntologyEntityAdminPort service;

    public OntologyEntityController(OntologyEntityAdminPort service) {
        this.service = service;
    }

    @GetMapping("/{entityType:regions|motifs|regional-embroideries|regional-motifs}")
    public List<OntologyEntityDetails> list(@PathVariable("entityType") String entityType) {
        return service.list(kind(entityType));
    }

    @GetMapping("/{entityType:regions|motifs|regional-embroideries|regional-motifs}/{localName}")
    public OntologyEntityDetails get(@PathVariable("entityType") String entityType,
                                     @PathVariable("localName") String localName) {
        return service.get(kind(entityType), localName);
    }

    @PostMapping("/{entityType:regions|motifs|regional-embroideries|regional-motifs}")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<OntologyEntityDetails> create(@PathVariable("entityType") String entityType,
                                                        @RequestBody OntologyEntityCommand command) {
        OntologyEntityDetails result = service.create(kind(entityType), command);
        return created(result, result.localName());
    }

    @PutMapping("/{entityType:regions|motifs|regional-embroideries|regional-motifs}/{localName}")
    public OntologyEntityDetails update(@PathVariable("entityType") String entityType,
                                        @PathVariable("localName") String localName,
                                        @RequestBody OntologyEntityCommand command) {
        return service.update(kind(entityType), localName, command);
    }

    @DeleteMapping("/{entityType:regions|motifs|regional-embroideries|regional-motifs}/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("entityType") String entityType,
                       @PathVariable("localName") String localName) {
        service.delete(kind(entityType), localName);
    }

    @PostMapping("/regions/synchronize-derived-types")
    public RegionDerivedTypeSynchronizationDetails synchronizeRegionDerivedTypes() {
        return service.synchronizeRegionDerivedTypes();
    }

    private OntologyEntityKind kind(String entityType) {
        return switch (entityType) {
            case "regions" -> OntologyEntityKind.REGION;
            case "motifs" -> OntologyEntityKind.MOTIF;
            case "regional-embroideries" -> OntologyEntityKind.REGIONAL_EMBROIDERY;
            case "regional-motifs" -> OntologyEntityKind.REGIONAL_MOTIF;
            default -> throw new IllegalArgumentException("Unsupported entity type: " + entityType);
        };
    }
}
