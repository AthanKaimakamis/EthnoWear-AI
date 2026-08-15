package fmi.ethnowear.application.service.archive.query;

import fmi.ethnowear.api.dto.archive.media.MediaFeatureAnnotationDetails;
import fmi.ethnowear.api.dto.archive.query.ArchiveItemDetailDetails;
import fmi.ethnowear.api.dto.archive.query.ArchiveItemMediaContentDetails;
import fmi.ethnowear.application.exceptions.ResourceNotFoundException;
import fmi.ethnowear.application.service.archive.item.ArchiveItemFeatureMapper;
import fmi.ethnowear.application.service.archive.item.ArchiveItemMapper;
import fmi.ethnowear.application.service.archive.media.ArchiveItemMediaMapper;
import fmi.ethnowear.application.service.archive.media.MediaAssetMapper;
import fmi.ethnowear.application.service.archive.media.MediaFeatureAnnotationMapper;
import fmi.ethnowear.dal.entity.ArchiveItem;
import fmi.ethnowear.dal.entity.BaseEntity;
import fmi.ethnowear.dal.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.dal.repository.ArchiveItemMediaRepository;
import fmi.ethnowear.dal.repository.ArchiveItemRepository;
import fmi.ethnowear.dal.repository.MediaFeatureAnnotationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveItemDetailService {

    private final ArchiveItemRepository archiveItemRepository;
    private final ArchiveItemFeatureRepository featureRepository;
    private final ArchiveItemMediaRepository mediaRepository;
    private final MediaFeatureAnnotationRepository annotationRepository;

    private final ArchiveItemMapper archiveItemMapper;
    private final ArchiveItemFeatureMapper featureMapper;
    private final ArchiveItemMediaMapper mediaMapper;
    private final MediaAssetMapper mediaAssetMapper;
    private final MediaFeatureAnnotationMapper annotationMapper;
    private final EntitySourceCitationMapper sourceCitationMapper;

    public ArchiveItemDetailDetails findById(Long id) {
        ArchiveItem archiveItem = archiveItemRepository
                .findOneById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Archive item", id));

        Map<Long, List<MediaFeatureAnnotationDetails>> annotationsByMedia = annotationRepository
                .findByArchiveItemMedia_ArchiveItem_Id(id)
                .stream()
                .collect(Collectors.groupingBy(
                                annotation -> annotation.getArchiveItemMedia().getId(),
                                Collectors.mapping(annotationMapper::toDetails, Collectors.toList())
                        )
                );

        List<ArchiveItemMediaContentDetails> media = mediaRepository
                .findByArchiveItemId(id)
                .stream()
                .sorted(Comparator.comparing(BaseEntity::getId))
                .map(itemMedia -> new ArchiveItemMediaContentDetails(
                        mediaMapper.toDetails(itemMedia),
                        mediaAssetMapper.toDetails(itemMedia.getMediaAsset()),
                        annotationsByMedia.getOrDefault(itemMedia.getId(), List.of())
                ))
                .toList();

        return new ArchiveItemDetailDetails(
                archiveItemMapper.toDetails(archiveItem),
                sourceCitationMapper.toDetails(archiveItem.getSourceReference()),
                featureRepository.findByArchiveItem_Id(id)
                        .stream()
                        .sorted(Comparator.comparing(BaseEntity::getId))
                        .map(featureMapper::toDetails)
                        .toList(),
                media
        );
    }
}
