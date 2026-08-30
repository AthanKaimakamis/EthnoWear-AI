package fmi.ethnowear.application.dto.document.query.history;

import fmi.ethnowear.application.dto.IdentifiableDto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record DocumentPageOcrResultDetails(
        Long id,
        Long documentPageId,
        Long documentPageMediaId,
        Long processingJobId,
        String rawText,
        String ocrEngine,
        String ocrEngineVersion,
        String ocrLanguage,
        BigDecimal ocrConfidence,
        boolean current,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {
}
