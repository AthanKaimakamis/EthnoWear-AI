package fmi.ethnowear.api.controller;

import fmi.ethnowear.ontology.admin.TechniqueCreateCommand;
import fmi.ethnowear.ontology.admin.TechniqueDetails;
import fmi.ethnowear.ontology.admin.TechniqueNotFoundException;
import fmi.ethnowear.ontology.admin.TechniqueOntologyAdminService;
import fmi.ethnowear.ontology.admin.TechniqueUpdateCommand;
import org.springframework.http.HttpStatus;
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

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TechniqueDetails create(@RequestBody TechniqueCreateCommand command) {
        return techniqueService.create(command);
    }

    @GetMapping("/{localName}")
    public TechniqueDetails get(@PathVariable("localName") String localName) {
        return techniqueService.get(localName)
                .orElseThrow(() -> new TechniqueNotFoundException(localName));
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
