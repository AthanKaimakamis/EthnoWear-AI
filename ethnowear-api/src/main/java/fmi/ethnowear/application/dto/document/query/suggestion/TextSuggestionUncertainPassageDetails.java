package fmi.ethnowear.application.dto.document.query.suggestion;

import java.math.BigDecimal;

public record TextSuggestionUncertainPassageDetails(
        String excerpt,
        String reason,
        BigDecimal confidence
) {
}
