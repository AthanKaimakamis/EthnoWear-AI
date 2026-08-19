package fmi.ethnowear.application.dto.document.query.quality;

import fmi.ethnowear.application.dto.IdentifiableDto;
import fmi.ethnowear.domain.model.document.quality.AssessmentType;
import fmi.ethnowear.domain.model.document.quality.AssessorType;
import fmi.ethnowear.domain.model.document.quality.QualityStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DocumentPageQualityAssessmentDetails(
        Long id,
        Long documentPageId,
        Long documentPageMediaId,
        Long processingJobId,
        Long documentPageOcrResultId,
        AssessmentType assessmentType,
        AssessorType assessorType,
        String assessorName,
        String assessorVersion,
        String scoreVersion,
        QualityStatus qualityStatus,
        BigDecimal overallScore,
        String summary,
        String limitations,
        boolean current,
        List<DocumentPageQualitySignalDetails> signals,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) implements IdentifiableDto {

    public DocumentPageQualityAssessmentDetails {
        signals = List.copyOf(signals);
    }
}
