package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.dto.archive.query.EntityContentDetails;
import fmi.ethnowear.application.dto.catalogue.EntityDetailDetails;
import fmi.ethnowear.application.dto.catalogue.EntityOntologyDetails;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.application.service.archive.query.ArchiveEvidenceService;
import fmi.ethnowear.application.service.archive.query.EntityContentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityDetailService {

    private final OntologyEntityDetailReader ontologyReader;
    private final EntityContentService contentService;
    private final ArchiveEvidenceService evidenceService;

    public EntityDetailDetails findByLocalName(FeatureType entityType, String localName, String language, Pageable evidencePageable) {
        if(evidencePageable == null)
            throw new IllegalArgumentException("Evidence pageable is required");

        EntityOntologyDetails ontology = ontologyReader.find(entityType, localName, language);
        EntityContentDetails content = contentService.findByOntologyEntity(entityType, ontology.iri(), ontology.language());
        Page<ArchiveEvidenceDetails> evidence = evidenceService.findByOntologyEntity(entityType, ontology.iri(), evidencePageable);

        return new EntityDetailDetails(ontology, content, evidence);
    }
}
