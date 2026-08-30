package fmi.ethnowear.application.dto.document.command.upload;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ReplacementRenditionUploadCommand(
        @Positive Long sourceReferenceId,
        boolean preferredOcrInput,
        boolean queueOcr,
        @Size(max = 2000) String mediaDescription,
        String renditionNotes
) {
}
