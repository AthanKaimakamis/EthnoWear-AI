package fmi.ethnowear.application.port.ontology.admin;

import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityCommand;
import fmi.ethnowear.application.dto.ontology.admin.OntologyEntityDetails;
import fmi.ethnowear.application.dto.ontology.admin.RegionDerivedTypeSynchronizationDetails;
import fmi.ethnowear.domain.model.ontology.OntologyEntityKind;

import java.util.List;

public interface OntologyEntityAdminPort {

    List<OntologyEntityDetails> list(OntologyEntityKind kind);

    OntologyEntityDetails get(OntologyEntityKind kind, String localName);

    OntologyEntityDetails create(OntologyEntityKind kind, OntologyEntityCommand command);

    OntologyEntityDetails update(
            OntologyEntityKind kind,
            String localName,
            OntologyEntityCommand command
    );

    void delete(OntologyEntityKind kind, String localName);

    RegionDerivedTypeSynchronizationDetails synchronizeRegionDerivedTypes();
}
