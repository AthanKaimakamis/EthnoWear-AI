package fmi.ethnowear.api.controller.ontology.admin;

import fmi.ethnowear.application.dto.ontology.admin.OrnamentCreateCommand;
import fmi.ethnowear.application.dto.ontology.admin.OrnamentDetails;
import fmi.ethnowear.application.exception.OrnamentNotFoundException;
import fmi.ethnowear.application.port.ontology.admin.OrnamentOntologyAdminPort;
import fmi.ethnowear.application.dto.ontology.admin.OrnamentUpdateCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static fmi.ethnowear.api.util.ResponseUtil.created;

@RestController
@RequestMapping("/api/admin/ontology/ornaments")
public class OrnamentOntologyController {

    private final OrnamentOntologyAdminPort ornamentService;

    public OrnamentOntologyController(OrnamentOntologyAdminPort ornamentService) {
        this.ornamentService = ornamentService;
    }

    @GetMapping
    public List<OrnamentDetails> list() {
        return ornamentService.list();
    }

    @GetMapping("/{localName}")
    public OrnamentDetails get(@PathVariable("localName") String localName) {
        return ornamentService.get(localName)
                .orElseThrow(() -> new OrnamentNotFoundException(localName));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<OrnamentDetails> create(@RequestBody OrnamentCreateCommand command) {
        OrnamentDetails result = ornamentService.create(command);
        return created(result, result.localName());
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
