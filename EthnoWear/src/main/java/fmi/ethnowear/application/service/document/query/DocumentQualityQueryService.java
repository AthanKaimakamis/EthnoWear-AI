package fmi.ethnowear.application.service.document.query;

import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualityAssessmentDetails;
import fmi.ethnowear.application.service.document.query.mapper.DocumentQualityMapper;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import fmi.ethnowear.persistence.jpa.projection.document.DocumentPageQualitySignalProjection;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualityAssessmentRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageQualitySignalRepository;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Unmodifiable;
import org.jspecify.annotations.NonNull;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static fmi.ethnowear.util.PageableUtils.boundedUnsorted;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocumentQualityQueryService {

    private static final int MAX_QUALITY_PAGE_SIZE = 100;

    private final DocumentQueryGuard queryGuard;
    private final DocumentPageQualityAssessmentRepository assessmentRepository;
    private final DocumentPageQualitySignalRepository signalRepository;
    private final DocumentQualityMapper qualityMapper;

    public List<DocumentPageQualityAssessmentDetails> findCurrentAssessments(
            Long documentId,
            Long pageId
    ) {
        queryGuard.requirePage(documentId, pageId);

        List<DocumentPageQualityAssessment> assessments = assessmentRepository
                .findByDocumentPage_IdAndCurrentTrueOrderByAssessmentTypeAscIdAsc(pageId);

        return qualityMapper.toDetails(assessments, findSignals(assessments));
    }

    public Page<DocumentPageQualityAssessmentDetails> findAssessmentHistory(
            Long documentId,
            Long pageId,
            Pageable pageable
    ) {
        queryGuard.requirePage(documentId, pageId);

        Page<DocumentPageQualityAssessment> assessments = assessmentRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        qualityPageable(pageable)
                );

        return qualityMapper.toDetails(assessments, findSignals(assessments.getContent()));
    }

    private @NonNull @Unmodifiable List<DocumentPageQualitySignalProjection> findSignals(
            @NonNull List<DocumentPageQualityAssessment> assessments
    ) {
        if(assessments.isEmpty())
            return List.of();

        List<Long> assessmentIds = assessments.stream()
                .map(DocumentPageQualityAssessment::getId)
                .toList();

        return signalRepository.findSafeSignals(assessmentIds);
    }

    @Contract("null -> fail")
    private @NonNull Pageable qualityPageable(Pageable pageable) {
        return boundedUnsorted(
                pageable,
                MAX_QUALITY_PAGE_SIZE,
                "Quality"
        );
    }
}
