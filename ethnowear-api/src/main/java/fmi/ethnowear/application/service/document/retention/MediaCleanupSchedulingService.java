package fmi.ethnowear.application.service.document.retention;

import fmi.ethnowear.application.dto.document.command.retention.MediaCleanupScheduleCommand;
import fmi.ethnowear.application.dto.document.query.retention.MediaCleanupEligibilityDetails;
import fmi.ethnowear.application.dto.document.query.retention.MediaCleanupScheduleDetails;
import fmi.ethnowear.application.exception.DocumentDependencyConflictException;
import fmi.ethnowear.application.exception.ResourceNotFoundException;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.application.service.document.query.mapper.DocumentHistoryMapper;
import fmi.ethnowear.config.MediaRetentionProperties;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageMediaRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;

import static fmi.ethnowear.util.IdentifierUtils.requireId;

@Service
@RequiredArgsConstructor
public class MediaCleanupSchedulingService {

    private final DocumentRepository documentRepository;
    private final DocumentPageMediaRepository pageMediaRepository;
    private final MediaAssetRepository mediaAssetRepository;
    private final MediaCleanupEligibilityService eligibilityService;
    private final GeneratedDocumentMediaPolicy generatedMediaPolicy;
    private final DocumentProcessingJobScheduler jobScheduler;
    private final DocumentHistoryMapper historyMapper;
    private final MediaRetentionProperties properties;
    private final Clock clock;

    @Transactional
    public MediaCleanupScheduleDetails schedule(
            Long documentId,
            MediaCleanupScheduleCommand command
    ) {
        requireId(documentId, "Document");

        MediaRetentionPolicy policy = command == null || command.policy() == null
                ? MediaRetentionPolicy.KEEP_ORIGINAL_ONLY
                : command.policy();

        if (policy != MediaRetentionPolicy.KEEP_ORIGINAL_ONLY)
            throw new IllegalArgumentException(
                    "Only KEEP_ORIGINAL_ONLY cleanup is supported"
            );

        Duration retentionPeriod = command == null
                || command.retentionDays() == null
                ? properties.getDefaultPeriod()
                : Duration.ofDays(command.retentionDays());

        if (retentionPeriod.isZero() || retentionPeriod.isNegative())
            throw new IllegalArgumentException(
                    "Media retention period must be positive"
            );

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Document",
                        documentId
                ));

        MediaCleanupEligibilityDetails eligibility =
                eligibilityService.evaluate(document, true);

        if (!eligibility.eligible())
            throw new DocumentDependencyConflictException(
                    String.join("; ", eligibility.blockers())
            );

        LocalDateTime retentionUntil = LocalDateTime.ofInstant(
                clock.instant().plus(retentionPeriod),
                ZoneOffset.UTC
        );

        LinkedHashSet<MediaAsset> assets = new LinkedHashSet<>();
        pageMediaRepository.findCleanupCandidates(
                documentId,
                generatedMediaPolicy.renditionTypes(),
                MediaOrigin.GENERATED,
                null
        ).forEach(media -> assets.add(media.getMediaAsset()));

        assets.forEach(asset -> asset.scheduleRetention(retentionUntil));
        mediaAssetRepository.saveAll(assets);

        DocumentProcessingJob job = jobScheduler.queueMediaCleanup(
                document,
                retentionUntil,
                policy
        );

        return new MediaCleanupScheduleDetails(
                documentId,
                policy,
                retentionUntil,
                assets.size(),
                historyMapper.toDetails(job)
        );
    }
}
