package fmi.ethnowear.persistence.jpa.projection.ontology;

import fmi.ethnowear.domain.model.ontology.OntologyVersionStatus;

import java.time.LocalDateTime;

public interface OntologyVersionSummaryProjection {
    Long getId();
    long getVersionNumber();
    LocalDateTime getCreatedAt();
    Long getCreatedByUserId();
    String getCreatedByUsername();
    String getChangeReason();
    String getContentHash();
    String getFileName();
    String getOntologyNamespace();
    boolean getValid();
    String getValidationMessage();
    Long getPreviousVersionId();
    Long getPreviousVersionNumber();
    Long getRestoredFromVersionId();
    Long getRestoredFromVersionNumber();
    OntologyVersionStatus getStatus();
}
