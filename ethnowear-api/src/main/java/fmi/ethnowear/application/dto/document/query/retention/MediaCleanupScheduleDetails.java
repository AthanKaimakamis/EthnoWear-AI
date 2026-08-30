package fmi.ethnowear.application.dto.document.query.retention;

import fmi.ethnowear.application.dto.document.query.history.DocumentProcessingJobDetails;
import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;

import java.time.LocalDateTime;

public record MediaCleanupScheduleDetails(
        Long documentId,
        MediaRetentionPolicy policy,
        LocalDateTime retentionUntil,
        int scheduledMediaCount,
        DocumentProcessingJobDetails job
) {
}
