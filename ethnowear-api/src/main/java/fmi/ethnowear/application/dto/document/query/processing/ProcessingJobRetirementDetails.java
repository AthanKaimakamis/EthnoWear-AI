package fmi.ethnowear.application.dto.document.query.processing;

import java.time.LocalDateTime;

public record ProcessingJobRetirementDetails(
        boolean retired,
        LocalDateTime retiredAt,
        String retiredBy,
        String reason
) {
}
