package fmi.ethnowear.application.port.ontology.admin;

import fmi.ethnowear.application.dto.ontology.admin.TechniqueCreateCommand;
import fmi.ethnowear.application.dto.ontology.admin.TechniqueDetails;
import fmi.ethnowear.application.dto.ontology.admin.TechniqueUpdateCommand;

import java.util.List;
import java.util.Optional;

public interface TechniqueOntologyAdminPort {

    TechniqueDetails create(TechniqueCreateCommand command);

    Optional<TechniqueDetails> get(String localName);

    List<TechniqueDetails> list();

    TechniqueDetails update(String localName, TechniqueUpdateCommand command);

    TechniqueDetails addCharacteristicRegion(String localName, String regionLocalName);

    TechniqueDetails removeCharacteristicRegion(String localName, String regionLocalName);

    void delete(String localName);
}
