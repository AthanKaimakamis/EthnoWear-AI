package fmi.ethnowear.application.port.ontology.admin;

import fmi.ethnowear.application.dto.ontology.admin.OrnamentCreateCommand;
import fmi.ethnowear.application.dto.ontology.admin.OrnamentDetails;
import fmi.ethnowear.application.dto.ontology.admin.OrnamentUpdateCommand;

import java.util.List;
import java.util.Optional;

public interface OrnamentOntologyAdminPort {

    OrnamentDetails create(OrnamentCreateCommand command);

    Optional<OrnamentDetails> get(String localName);

    List<OrnamentDetails> list();

    OrnamentDetails update(String localName, OrnamentUpdateCommand command);

    OrnamentDetails addCharacteristicRegion(String localName, String regionLocalName);

    OrnamentDetails removeCharacteristicRegion(String localName, String regionLocalName);

    void delete(String localName);
}
