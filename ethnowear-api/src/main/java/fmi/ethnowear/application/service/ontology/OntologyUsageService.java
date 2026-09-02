package fmi.ethnowear.application.service.ontology;

import fmi.ethnowear.application.exception.OntologyEntityException;
import fmi.ethnowear.application.port.ontology.admin.OntologyUsageGuard;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import fmi.ethnowear.persistence.jpa.repository.KnowledgeChunkRepository;
import fmi.ethnowear.persistence.jpa.repository.MediaEntityLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OntologyUsageService implements OntologyUsageGuard {

    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final MediaEntityLinkRepository mediaEntityLinkRepository;
    private final KnowledgeChunkRepository knowledgeChunkRepository;

    @Override
    public void requireUnused(Collection<String> ontologyIris) {
        List<String> iris = ontologyIris == null
                ? List.of()
                : ontologyIris.stream().filter(iri -> iri != null).distinct().toList();
        if(iris.isEmpty())
            return;

        boolean used = archiveItemRepository.existsByOntologyClassification(iris)
                || featureRepository.existsByOntologyIriIn(iris)
                || mediaEntityLinkRepository.existsByOntologyIriIn(iris)
                || knowledgeChunkRepository.existsByOntologyIriIn(iris);
        if(used)
            throw new OntologyEntityException(
                    OntologyEntityException.Reason.IN_USE,
                    "Ontology resource is referenced by persisted application data"
            );
    }
}
