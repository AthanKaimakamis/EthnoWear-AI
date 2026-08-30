package fmi.ethnowear.application.service.document.retention;

import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.archive.media.storage.MediaFileHasher;
import fmi.ethnowear.application.service.archive.media.storage.MediaPathResolver;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;
import fmi.ethnowear.domain.model.media.MediaStorageState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentProcessingJobRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static fmi.ethnowear.util.TextUtils.isBlank;

@Service
@RequiredArgsConstructor
public class MediaAssetPurgeService {

    private static final String PURGE_REASON =
            "Generated document media removed by KEEP_ORIGINAL_ONLY retention policy";

    private final MediaAssetRepository mediaAssetRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final DocumentProcessingJobRepository jobRepository;
    private final DocumentPageFigureRepository figureRepository;
    private final GeneratedDocumentMediaPolicy generatedMediaPolicy;
    private final MediaPathResolver paths;
    private final MediaFileHasher fileHasher;
    private final ManagementEventPublisher managementEvents;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean purge(Long mediaAssetId) {
        MediaAsset asset = mediaAssetRepository.findByIdForUpdate(mediaAssetId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Media asset",
                        mediaAssetId
                ));

        if (asset.getStorageState() == MediaStorageState.PURGED)
            return false;

        validate(asset);

        if (figureRepository.existsByMediaAsset_Id(mediaAssetId))
            throw new DocumentDependencyConflictException(
                    "Page figure media must be preserved while its figure exists"
            );

        if (jobRepository
                .existsByInputMediaAsset_IdAndActiveJobKeyIsNotNullAndJobTypeNot(
                        mediaAssetId,
                        JobType.MEDIA_CLEANUP
                ))
            throw new DocumentDependencyConflictException(
                    "Generated media is referenced by an active processing job"
            );

        List<DocumentPageMedia> links = pageMediaRepository
                .findByMediaAsset_Id(mediaAssetId);

        if (links.isEmpty() || links.stream().anyMatch(media ->
                !generatedMediaPolicy.isGenerated(media)))
            throw new DocumentDependencyConflictException(
                    "Media is not an exclusively generated document rendition"
            );

        deleteContent(asset);

        links.stream()
                .filter(DocumentPageMedia::isPreferredOcrInput)
                .forEach(media -> media.setPreferredOcrInput(false));
        pageMediaRepository.saveAll(links);

        asset.markPurged(now(), PURGE_REASON);
        mediaAssetRepository.saveAndFlush(asset);
        managementEvents.media(asset, ManagementEvent.Action.DELETED);
        return true;
    }

    private void validate(MediaAsset asset) {
        if (asset.getOrigin() != MediaOrigin.GENERATED
                || asset.getRetentionPolicy()
                != MediaRetentionPolicy.KEEP_ORIGINAL_ONLY)
            throw new DocumentDependencyConflictException(
                    "Only generated KEEP_ORIGINAL_ONLY media can be purged"
            );

        if (asset.getRetentionUntil() == null
                || asset.getRetentionUntil().isAfter(now()))
            throw new DocumentDependencyConflictException(
                    "Media retention period has not passed"
            );

        if (!isBlank(asset.getStorageUrl()))
            throw new DocumentDependencyConflictException(
                    "Externally stored media cannot be purged by this service"
            );

        if (isBlank(asset.getFilePath()))
            throw new DocumentDependencyConflictException(
                    "Generated media has no managed storage key"
            );
    }

    private void deleteContent(MediaAsset asset) {
        try {
            Path file = paths.resolveExisting(asset.getFilePath());

            if (Files.exists(file)) {
                String actualChecksum = fileHasher.sha256(file);

                if (asset.getChecksum() == null
                        || !asset.getChecksum().equals(actualChecksum))
                    throw new DocumentDependencyConflictException(
                            "Generated media checksum does not match its metadata"
                    );

                Files.delete(file);
            }

            if (!isBlank(asset.getThumbnailPath()))
                Files.deleteIfExists(
                        paths.resolveExisting(asset.getThumbnailPath())
                );
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Could not remove generated media content",
                    ex
            );
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
