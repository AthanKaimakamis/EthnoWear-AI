package fmi.ethnowear.application.dto.document.query;

import java.time.LocalDateTime;

public record DocumentPageRetirementDetails(
        boolean retired,
        LocalDateTime retiredAt,
        String retiredBy,
        String reason
) {
}
