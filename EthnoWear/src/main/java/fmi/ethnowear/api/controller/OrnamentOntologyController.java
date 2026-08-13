package fmi.ethnowear.api.controller;

import fmi.ethnowear.ontology.admin.OrnamentCreateCommand;
import fmi.ethnowear.ontology.admin.OrnamentDetails;
import fmi.ethnowear.application.exceptions.OrnamentNotFoundException;
import fmi.ethnowear.ontology.admin.OrnamentOntologyAdminService;
import fmi.ethnowear.ontology.admin.OrnamentUpdateCommand;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ontology/ornaments")
public class OrnamentOntologyController {

    private final OrnamentOntologyAdminService ornamentService;

    public OrnamentOntologyController(OrnamentOntologyAdminService ornamentService) {
        this.ornamentService = ornamentService;
    }

    @GetMapping
    public List<OrnamentDetails> list() {
        return ornamentService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrnamentDetails create(@RequestBody OrnamentCreateCommand command) {
        return ornamentService.create(command);
    }

    @GetMapping("/{localName}")
    public OrnamentDetails get(@PathVariable("localName") String localName) {
        return ornamentService.get(localName)
                .orElseThrow(() -> new OrnamentNotFoundException(localName));
    }

    @PutMapping("/{localName}")
    public OrnamentDetails update(
            @PathVariable("localName") String localName,
            @RequestBody OrnamentUpdateCommand command
    ) {
        return ornamentService.update(localName, command);
    }

    @PutMapping("/{localName}/characteristic-regions/{regionLocalName}")
    public OrnamentDetails addCharacteristicRegion(
            @PathVariable("localName") String localName,
            @PathVariable("regionLocalName") String regionLocalName
    ) {
        return ornamentService.addCharacteristicRegion(localName, regionLocalName);
    }

    @DeleteMapping("/{localName}/characteristic-regions/{regionLocalName}")
    public OrnamentDetails removeCharacteristicRegion(
            @PathVariable("localName") String localName,
            @PathVariable("regionLocalName") String regionLocalName
    ) {
        return ornamentService.removeCharacteristicRegion(localName, regionLocalName);
    }

    @DeleteMapping("/{localName}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("localName") String localName) {
        ornamentService.delete(localName);
    }

}
