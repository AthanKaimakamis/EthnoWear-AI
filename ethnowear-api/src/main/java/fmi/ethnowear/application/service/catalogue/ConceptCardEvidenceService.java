package fmi.ethnowear.application.service.catalogue;

import fmi.ethnowear.application.dto.catalogue.ConceptEvidenceSummaryDetails;
import fmi.ethnowear.domain.model.archive.MediaRole;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemMedia;
import fmi.ethnowear.persistence.jpa.projection.OntologyEvidenceLinkProjection;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ConceptCardEvidenceService {

    private static final List<MediaRole> REPRESENTATIVE_ROLES = List.of(
            MediaRole.THUMBNAIL,
            MediaRole.PRIMARY,
            MediaRole.DETAIL
    );

    private final ArchiveItemRepository itemRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final ArchiveItemMediaRepository mediaRepository;

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
        Map<Long, ArchiveItemMedia> representativeByItem = representativeMedia(itemIds);

        Map<String, ConceptEvidenceSummaryDetails> result = new LinkedHashMap<>();
        distinctIris.forEach(iri -> {
            Set<Long> conceptItemIds = itemIdsByIri.getOrDefault(iri, Set.of());
            Long representativeMediaAssetId = conceptItemIds.stream()
                    .map(representativeByItem::get)
                    .filter(media -> media != null)
                    .min(representativeComparator())
                    .map(media -> media.getMediaAsset().getId())
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
            default -> featureRepository.findPublishedEvidenceLinks(entityType, ontologyIris);
        };
    }

    private Map<Long, ArchiveItemMedia> representativeMedia(Set<Long> itemIds) {
        if(itemIds.isEmpty())
            return Map.of();

        return mediaRepository
                .findPublicByArchiveItemIdsRolesAndMediaType(
                        itemIds,
                        REPRESENTATIVE_ROLES,
                        MediaType.IMAGE,
                        FigureReviewState.APPROVED
                )
                .stream()
                .sorted(representativeComparator())
                .collect(Collectors.toMap(
                        media -> media.getArchiveItem().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private Comparator<ArchiveItemMedia> representativeComparator() {
        return Comparator
                .comparingInt((ArchiveItemMedia media) -> REPRESENTATIVE_ROLES.indexOf(media.getRole()))
                .thenComparing(ArchiveItemMedia::getId);
    }
}
