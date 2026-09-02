package fmi.ethnowear.application.service.ontology.version;

import fmi.ethnowear.application.dto.ontology.admin.OntologyVersionDetails;
import fmi.ethnowear.persistence.jpa.projection.ontology.OntologyVersionSummaryProjection;
import org.springframework.stereotype.Component;

@Component
public class OntologyVersionMapper {

    public OntologyVersionDetails toDetails(OntologyVersionSummaryProjection version) {
        return new OntologyVersionDetails(
                version.getId(),
                version.getVersionNumber(),
                version.getCreatedAt(),
                version.getCreatedByUserId(),
                version.getCreatedByUsername(),
                version.getChangeReason(),
                version.getContentHash(),
                version.getFileName(),
                version.getOntologyNamespace(),
                version.getValid(),
                version.getValidationMessage(),
                version.getPreviousVersionId(),
                version.getPreviousVersionNumber(),
                version.getRestoredFromVersionId(),
                version.getRestoredFromVersionNumber(),
                version.getStatus()
        );
    }
}
