package fmi.ethnowear.application.dto.document.command.retention;

import fmi.ethnowear.domain.model.media.MediaRetentionPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record MediaCleanupScheduleCommand(
        MediaRetentionPolicy policy,
        @Min(1) @Max(3650) Integer retentionDays
) {
}
