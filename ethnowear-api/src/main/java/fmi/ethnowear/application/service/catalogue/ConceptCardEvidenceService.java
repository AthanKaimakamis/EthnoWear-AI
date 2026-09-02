package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import fmi.ethnowear.application.service.archive.media.asset.PublicRepresentativeMediaService;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.persistence.jpa.projection.OntologyEvidenceLinkProjection;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConceptCardEvidenceService {

    private final ArchiveItemRepository itemRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final PublicRepresentativeMediaService representativeMediaService;

    public Map<String, ConceptEvidenceSummaryDetails> summarize(
            FeatureType entityType,
            Collection<String> ontologyIris
    ) {
        if(entityType == null)
            throw new IllegalArgumentException("Ontology entity type is required");

        List<String> distinctIris = ontologyIris == null
                ? List.of()
                : ontologyIris.stream().distinct().toList();
        if(distinctIris.isEmpty())
            return Map.of();

        Map<String, Set<Long>> itemIdsByIri = links(entityType, distinctIris)
                .stream()
                .collect(Collectors.groupingBy(
                        OntologyEvidenceLinkProjection::getOntologyIri,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                OntologyEvidenceLinkProjection::getArchiveItemId,
                                Collectors.toCollection(LinkedHashSet::new)
                        )
                ));
        Set<Long> itemIds = itemIdsByIri.values()
                .stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        Map<Long, Long> representativeByItem =
                representativeMediaService.findByArchiveItemIds(itemIds);

        Map<String, ConceptEvidenceSummaryDetails> result = new LinkedHashMap<>();
        distinctIris.forEach(iri -> {
            Set<Long> conceptItemIds = itemIdsByIri.getOrDefault(iri, Set.of());
            Long representativeMediaAssetId = representativeByItem.entrySet()
                    .stream()
                    .filter(entry -> conceptItemIds.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
            result.put(iri, new ConceptEvidenceSummaryDetails(
                    conceptItemIds.size(),
                    representativeMediaAssetId
            ));
        });

        return Map.copyOf(result);
    }

    private List<OntologyEvidenceLinkProjection> links(FeatureType entityType, List<String> ontologyIris) {
        return switch(entityType) {
            case REGION -> itemRepository.findPublishedRegionEvidenceLinks(ontologyIris);
            case REGIONAL_EMBROIDERY -> itemRepository
                    .findPublishedRegionalEmbroideryEvidenceLinks(ontologyIris);
            case REGIONAL_MOTIF -> itemRepository
                    .findPublishedRegionalMotifEvidenceLinks(ontologyIris);
            default -> featureRepository.findPublishedEvidenceLinks(entityType, ontologyIris);
        };
    }

}
