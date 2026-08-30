package fmi.ethnowear.application.service.document.query.mapper;

import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualityAssessmentDetails;
import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualitySignalDetails;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPageQualitySignalProjection;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.math.RoundingMode;
import fmi.ethnowear.domain.model.document.quality.QualitySignalSeverity;

@Component
public class DocumentQualityMapper {

    public List<DocumentPageQualityAssessmentDetails> toDetails(
            @NonNull Collection<DocumentPageQualityAssessment> assessments,
            @NonNull Collection<DocumentPageQualitySignalProjection> signals
    ) {
        Map<Long, List<DocumentPageQualitySignalDetails>>
                signalsByAssessment = signals.stream()
                .map(this::toDetails)
                .collect(Collectors.groupingBy(
                        DocumentPageQualitySignalDetails::assessmentId,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        return assessments.stream()
                .map(assessment -> toDetails(
                        assessment,
                        signalsByAssessment.getOrDefault(
                                assessment.getId(),
                                List.of()
                        )
                ))
                .toList();
    }

    public Page<DocumentPageQualityAssessmentDetails> toDetails(
            @NonNull Page<DocumentPageQualityAssessment> assessments,
            Collection<DocumentPageQualitySignalProjection> signals
    ) {
        return new PageImpl<>(
                toDetails(assessments.getContent(), signals),
                assessments.getPageable(),
                assessments.getTotalElements()
        );
    }

    public DocumentPageQualityAssessmentDetails toDetails(
            @NonNull DocumentPageQualityAssessment assessment,
            List<DocumentPageQualitySignalDetails> signals
    ) {
        return new DocumentPageQualityAssessmentDetails(
                assessment.getId(),
                id(assessment.getDocumentPage()),
                id(assessment.getDocumentPageMedia()),
                id(assessment.getProcessingJob()),
                id(assessment.getDocumentPageOcrResult()),
                assessment.getAssessmentType(),
                assessment.getAssessorType(),
                assessment.getAssessorName(),
                assessment.getAssessorVersion(),
                assessment.getScoreVersion(),
                assessment.getQualityStatus(),
                assessment.getOverallScore(),
                assessment.getOverallScore() == null
                        ? null
                        : assessment.getOverallScore()
                                .movePointRight(2)
                                .setScale(2, RoundingMode.HALF_UP),
                assessment.getSummary(),
                assessment.getLimitations(),
                assessment.isCurrent(),
                signals,
                signals.stream()
                        .filter(signal -> signal.severity() == QualitySignalSeverity.INFO)
                        .toList(),
                signals.stream()
                        .filter(signal -> signal.severity() != QualitySignalSeverity.INFO)
                        .toList(),
                assessment.getCreatedAt(),
                assessment.getUpdatedAt()
        );
    }

    public DocumentPageQualitySignalDetails toDetails(
            @NonNull DocumentPageQualitySignalProjection signal
    ) {
        return new DocumentPageQualitySignalDetails(
                signal.getId(),
                signal.getAssessmentId(),
                signal.getSignalType(),
                signal.getSignalOrdinal(),
                signal.getSignalValueDecimal(),
                signal.getSignalValueText(),
                signal.getSeverity(),
                signal.getWeight(),
                signal.getMessage(),
                signal.getCreatedAt(),
                signal.getUpdatedAt()
        );
    }

    private Long id(AppendOnlyEntity entity) {
        return entity == null ? null : entity.getId();
    }
}
