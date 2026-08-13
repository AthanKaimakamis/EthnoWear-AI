package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.api.dto.archive.query.ArchiveEvidenceDetails;
import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.application.enums.MediaRole;
import fmi.ethnowear.dal.entity.ArchiveItem;
import fmi.ethnowear.dal.entity.ArchiveItemFeature;
import fmi.ethnowear.dal.entity.ArchiveItemMedia;
import fmi.ethnowear.dal.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.dal.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.dal.repository.ArchiveItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveEvidenceService {

    private static final List<MediaRole> PREVIEW_ROLES = List.of(
            MediaRole.THUMBNAIL,
            MediaRole.PRIMARY,
            MediaRole.DETAIL
    );

    private final ArchiveItemRepository itemRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final ArchiveItemMediaRepository mediaRepository;
    private final ArchiveEvidenceMapper evidenceMapper;

    public Page<ArchiveEvidenceDetails> findByOntologyEntity(FeatureType entityType, String ontologyIri, Pageable pageable) {
        validate(entityType, ontologyIri, pageable);

        Page<ArchiveItem> items = itemRepository.findOntologyEvidence(
                entityType,
                ontologyIri,
                entityType == FeatureType.REGION,
                entityType == FeatureType.REGIONAL_EMBROIDERY,
                pageable
        );

        if(items.isEmpty())
            return items.map(item -> evidenceMapper.toDetails(item, false, List.of(), null));

        List<Long> itemIds = items.stream().map(ArchiveItem::getId).toList();
        Map<Long, List<ArchiveItemFeature>> featuresByItem = findFeatures(itemIds, entityType, ontologyIri);
        Map<Long, ArchiveItemMedia> previewByItem = findPreviewMedia(itemIds);

        return items.map(item -> evidenceMapper.toDetails(
                item,
                isDirectlyLinked(item, entityType, ontologyIri),
                featuresByItem.getOrDefault(item.getId(), List.of()),
                previewByItem.get(item.getId())
        ));
    }

    private Map<Long, List<ArchiveItemFeature>> findFeatures(List<Long> itemIds, FeatureType entityType, String ontologyIri) {
        return featureRepository
                .findByArchiveItem_IdInAndFeatureTypeAndOntologyIriAndValidatedTrue(
                        itemIds,
                        entityType,
                        ontologyIri
                )
                .stream()
                .collect(Collectors.groupingBy(feature -> feature.getArchiveItem().getId()));
    }

    private Map<Long, ArchiveItemMedia> findPreviewMedia(List<Long> itemIds) {
        Comparator<ArchiveItemMedia> previewOrder = Comparator
                .comparingInt((ArchiveItemMedia media) -> PREVIEW_ROLES.indexOf(media.getRole()))
                .thenComparing(ArchiveItemMedia::getId);

        return mediaRepository.findByArchiveItem_IdInAndRoleIn(itemIds, PREVIEW_ROLES)
                .stream()
                .sorted(previewOrder)
                .collect(Collectors.toMap(
                        media -> media.getArchiveItem().getId(),
                        Function.identity(),
                        (first, ignored) -> first
                ));
    }

    private boolean isDirectlyLinked(ArchiveItem item, FeatureType entityType, String ontologyIri) {
        if(entityType == FeatureType.REGION)
            return Objects.equals(item.getOntologyRegionIri(), ontologyIri);

        if(entityType == FeatureType.REGIONAL_EMBROIDERY)
            return Objects.equals(item.getOntologyRegionalEmbroideryIri(), ontologyIri);

        return false;
    }

    private void validate(FeatureType entityType, String ontologyIri, Pageable pageable) {
        if(entityType == null)
            throw new IllegalArgumentException("Ontology entity type is required");

        if(isBlank(ontologyIri))
            throw new IllegalArgumentException("Ontology IRI is required");

        if(pageable == null)
            throw new IllegalArgumentException("Pageable is required");
    }

}
