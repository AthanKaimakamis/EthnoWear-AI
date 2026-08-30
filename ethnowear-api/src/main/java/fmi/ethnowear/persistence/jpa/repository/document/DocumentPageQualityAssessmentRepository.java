package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.Collection;

import fmi.ethnowear.domain.model.document.quality.AssessmentType;

public interface DocumentPageQualityAssessmentRepository extends JpaRepository<DocumentPageQualityAssessment, Long> {

    Page<DocumentPageQualityAssessment> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );

    List<DocumentPageQualityAssessment> findByDocumentPage_IdAndCurrentTrueOrderByAssessmentTypeAscIdAsc(
            Long documentPageId
    );

    Optional<DocumentPageQualityAssessment> findByProcessingJob_Id(Long processingJobId);

    List<DocumentPageQualityAssessment> findByProcessingJob_IdIn(
            Collection<Long> processingJobIds
    );

    Optional<DocumentPageQualityAssessment>
    findByDocumentPage_IdAndDocumentPageMedia_IdAndAssessmentTypeAndCurrentTrue(
            Long documentPageId,
            Long documentPageMediaId,
            AssessmentType assessmentType
    );
}
