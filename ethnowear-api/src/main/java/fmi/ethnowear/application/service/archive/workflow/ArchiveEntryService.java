package fmi.ethnowear.application.service.archive.workflow;

import fmi.ethnowear.application.dto.archive.item.ArchiveItemDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureDetails;
import fmi.ethnowear.application.dto.archive.item.ArchiveItemFeatureWriteDto;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaDetails;
import fmi.ethnowear.application.dto.archive.media.ArchiveItemMediaWriteDto;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryDetails;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryFeatureWriteDto;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryMediaWriteDto;
import fmi.ethnowear.application.dto.archive.workflow.ArchiveEntryWriteDto;
import fmi.ethnowear.application.service.archive.item.ArchiveItemFeatureService;
import fmi.ethnowear.application.service.archive.item.ArchiveItemFeatureMapper;
import fmi.ethnowear.application.service.archive.item.ArchiveItemService;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaService;
import fmi.ethnowear.application.service.archive.media.attachment.ArchiveItemMediaMapper;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemFeatureRepository;
import fmi.ethnowear.persistence.jpa.repository.ArchiveItemMediaRepository;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ArchiveEntryService {

    private final ArchiveItemService archiveItemService;
    private final ArchiveItemFeatureService featureService;
    private final ArchiveItemMediaService mediaService;
    private final ArchiveItemFeatureRepository featureRepository;
    private final ArchiveItemMediaRepository mediaRepository;
    private final ArchiveItemFeatureMapper featureMapper;
    private final ArchiveItemMediaMapper mediaMapper;

    public ArchiveEntryDetails findById(Long archiveItemId) {
        ArchiveItemDetails item = archiveItemService.findById(archiveItemId);
        List<ArchiveItemFeatureDetails> features = featureRepository
                .findByArchiveItem_Id(archiveItemId)
                .stream()
                .map(featureMapper::toDetails)
                .toList();
        List<ArchiveItemMediaDetails> media = mediaRepository
                .findByArchiveItemId(archiveItemId)
                .stream()
                .map(mediaMapper::toDetails)
                .toList();

        return new ArchiveEntryDetails(item, features, media);
    }

    @Transactional
    public ArchiveEntryDetails create(ArchiveEntryWriteDto input) {
        validate(input, true);

        ArchiveItemDetails item = archiveItemService.create(input.archiveItem());
        Long archiveItemId = item.id();
        List<ArchiveItemFeatureDetails> features = input.features()
                .stream()
                .map(feature -> featureService.create(toFeatureWriteDto(archiveItemId, feature)))
                .toList();
        List<ArchiveItemMediaDetails> media = input.media()
                .stream()
                .map(itemMedia -> mediaService.create(toMediaWriteDto(archiveItemId, itemMedia)))
                .toList();

        return new ArchiveEntryDetails(item, features, media);
    }

    @Transactional
    public ArchiveEntryDetails update(Long archiveItemId, ArchiveEntryWriteDto input) {
        requireId(archiveItemId, "Archive item");
        validate(input, false);

        List<Long> existingFeatureIds = featureRepository.findByArchiveItem_Id(archiveItemId)
                .stream()
                .map(feature -> feature.getId())
                .toList();
        List<Long> existingMediaIds = mediaRepository.findByArchiveItemId(archiveItemId)
                .stream()
                .map(itemMedia -> itemMedia.getId())
                .toList();
        validateOwnership(
                input.features().stream().map(ArchiveEntryFeatureWriteDto::id).toList(),
                existingFeatureIds,
                "Archive item feature"
        );
        validateOwnership(
                input.media().stream().map(ArchiveEntryMediaWriteDto::id).toList(),
                existingMediaIds,
                "Archive item media"
        );

        ArchiveItemDetails item = archiveItemService.update(archiveItemId, input.archiveItem());
        Set<Long> retainedFeatureIds = ids(
                input.features().stream().map(ArchiveEntryFeatureWriteDto::id).toList()
        );
        Set<Long> retainedMediaIds = ids(
                input.media().stream().map(ArchiveEntryMediaWriteDto::id).toList()
        );

        existingFeatureIds.stream()
                .filter(id -> !retainedFeatureIds.contains(id))
                .forEach(featureService::delete);
        existingMediaIds.stream()
                .filter(id -> !retainedMediaIds.contains(id))
                .forEach(mediaService::delete);

        List<ArchiveItemFeatureDetails> features = input.features()
                .stream()
                .map(feature -> saveFeature(archiveItemId, feature))
                .toList();
        List<ArchiveItemMediaDetails> media = input.media()
                .stream()
                .map(itemMedia -> saveMedia(archiveItemId, itemMedia))
                .toList();

        return new ArchiveEntryDetails(item, features, media);
    }

    private ArchiveItemFeatureDetails saveFeature(Long archiveItemId, @NonNull ArchiveEntryFeatureWriteDto input) {
        ArchiveItemFeatureWriteDto writeDto = toFeatureWriteDto(archiveItemId, input);
        return input.id() == null
                ? featureService.create(writeDto)
                : featureService.update(input.id(), writeDto);
    }

    private ArchiveItemMediaDetails saveMedia(Long archiveItemId, @NonNull ArchiveEntryMediaWriteDto input) {
        ArchiveItemMediaWriteDto writeDto = toMediaWriteDto(archiveItemId, input);
        return input.id() == null
                ? mediaService.create(writeDto)
                : mediaService.update(input.id(), writeDto);
    }

    private @NonNull ArchiveItemFeatureWriteDto toFeatureWriteDto(
            Long archiveItemId,
            @NonNull ArchiveEntryFeatureWriteDto input
    ) {
        return new ArchiveItemFeatureWriteDto(
                archiveItemId,
                input.featureType(),
                input.ontologyIri(),
                input.ontologyLocalName(),
                input.confidence(),
                input.validated(),
                input.notes(),
                input.sourceReferenceId()
        );
    }

    private @NonNull ArchiveItemMediaWriteDto toMediaWriteDto(
            Long archiveItemId,
            @NonNull ArchiveEntryMediaWriteDto input
    ) {
        return new ArchiveItemMediaWriteDto(
                archiveItemId,
                input.mediaAssetId(),
                input.role(),
                input.captionBg(),
                input.captionEn()
        );
    }

    private void validate(ArchiveEntryWriteDto input, boolean creating) {
        if(input == null)
            throw new IllegalArgumentException("Archive entry input is required");

        if(input.archiveItem() == null)
            throw new IllegalArgumentException("Archive item input is required");

        validateUniqueIds(input.features().stream().map(ArchiveEntryFeatureWriteDto::id).toList(),
                "Archive item feature");
        validateUniqueIds(input.media().stream().map(ArchiveEntryMediaWriteDto::id).toList(),
                "Archive item media");

        if(creating && (input.features().stream().anyMatch(feature -> feature.id() != null)
                || input.media().stream().anyMatch(itemMedia -> itemMedia.id() != null)))
            throw new IllegalArgumentException("Child identifiers are not allowed when creating an archive entry");
    }

    private void validateUniqueIds(List<Long> values, String resourceName) {
        Set<Long> identifiers = new HashSet<>();
        values.stream()
                .filter(id -> id != null)
                .forEach(id -> {
                    requireId(id, resourceName);
                    if(!identifiers.add(id))
                        throw new IllegalArgumentException(resourceName + " identifier is duplicated: " + id);
                });
    }

    private void validateOwnership(List<Long> requestedIds, List<Long> existingIds, String resourceName) {
        requestedIds.stream()
                .filter(id -> id != null)
                .filter(id -> !existingIds.contains(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new IllegalArgumentException(resourceName + " does not belong to archive item: " + id);
                });
    }

    private @NonNull Set<Long> ids(List<Long> values) {
        Set<Long> identifiers = new HashSet<>();
        values.stream().filter(id -> id != null).forEach(identifiers::add);
        return identifiers;
    }
}
