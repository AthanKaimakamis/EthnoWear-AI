package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageQualityAssessment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DocumentPageQualityAssessmentRepository extends JpaRepository<DocumentPageQualityAssessment, Long> {

    Page<DocumentPageQualityAssessment> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );

    List<DocumentPageQualityAssessment> findByDocumentPage_IdAndCurrentTrueOrderByAssessmentTypeAscIdAsc(
            Long documentPageId
    );
}
