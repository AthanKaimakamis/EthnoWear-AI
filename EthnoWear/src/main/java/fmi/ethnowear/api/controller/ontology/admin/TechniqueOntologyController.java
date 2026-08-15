package fmi.ethnowear.api.controller.ontology.admin;

import fmi.ethnowear.ontology.admin.command.TechniqueCreateCommand;
import fmi.ethnowear.ontology.admin.model.TechniqueDetails;
import fmi.ethnowear.application.exceptions.TechniqueNotFoundException;
import fmi.ethnowear.ontology.admin.TechniqueOntologyAdminService;
import fmi.ethnowear.ontology.admin.command.TechniqueUpdateCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static fmi.ethnowear.api.util.ResponseUtil.created;

@RestController
@RequestMapping("/api/admin/ontology/techniques")
public class TechniqueOntologyController {

    private final TechniqueOntologyAdminService techniqueService;

    public TechniqueOntologyController(TechniqueOntologyAdminService techniqueService) {
        this.techniqueService = techniqueService;
    }

    @GetMapping
    public List<TechniqueDetails> list() {
        return techniqueService.list();
    }

    @GetMapping("/{localName}")
    public TechniqueDetails get(@PathVariable("localName") String localName) {
        return techniqueService
                .get(localName)
                .orElseThrow(() -> new TechniqueNotFoundException(localName));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TechniqueDetails> create(@RequestBody TechniqueCreateCommand command) {
        TechniqueDetails result = techniqueService.create(command);
        return created(result, result.localName());
    }

    @PutMapping("/{localName}")
    public TechniqueDetails update(
            @PathVariable("localName") String localName,
            @RequestBody TechniqueUpdateCommand command
    ) {
        return techniqueService.update(localName, command);
    }

    @PutMapping("/{localName}/characteristic-regions/{regionLocalName}")
    public TechniqueDetails addCharacteristicRegion(
            @PathVariable("localName") String localName,
            @PathVariable("regionLocalName") String regionLocalName
    ) {
        return techniqueService.addCharacteristicRegion(localName, regionLocalName);
    }

    @DeleteMapping("/{localName}/characteristic-regions/{regionLocalName}")
    public TechniqueDetails removeCharacteristicRegion(
            @PathVariable("localName") String localName,
            @PathVariable("regionLocalName") String regionLocalName
    ) {
        return techniqueService.removeCharacteristicRegion(localName, regionLocalName);
    }

    @DeleteMapping("/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("localName") String localName) {
        techniqueService.delete(localName);
    }
}
