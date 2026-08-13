package fmi.ethnowear.api.dto.catalogue;

import fmi.ethnowear.api.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.api.dto.archive.query.EntityContentDetails;
import org.springframework.data.domain.Page;

public record EntityDetailDetails(
        EntityOntologyDetails ontology,
        EntityContentDetails content,
        Page<ArchiveEvidenceDetails> evidence
) {
}
