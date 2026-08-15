package fmi.ethnowear.application.dto.catalogue;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.dto.archive.query.EntityContentDetails;
import org.springframework.data.domain.Page;

public record EntityDetailDetails(
        EntityOntologyDetails ontology,
        EntityContentDetails content,
        Page<ArchiveEvidenceDetails> evidence
) {
}
