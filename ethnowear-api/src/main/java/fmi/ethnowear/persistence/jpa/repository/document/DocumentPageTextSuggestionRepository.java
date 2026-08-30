package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageTextSuggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;

public interface DocumentPageTextSuggestionRepository
        extends JpaRepository<DocumentPageTextSuggestion, Long> {

    @EntityGraph(attributePaths = {
            "documentPage",
            "documentPageMedia",
            "documentPageOcrResult",
            "processingJob"
    })
    Optional<DocumentPageTextSuggestion> findByProcessingJob_Id(Long processingJobId);

    @EntityGraph(attributePaths = {
            "documentPage",
            "documentPageMedia",
            "documentPageOcrResult",
            "processingJob"
    })
    Optional<DocumentPageTextSuggestion>
    findFirstByDocumentPage_IdAndDocumentPageOcrResult_IdAndProcessingJob_StatusOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Long documentPageOcrResultId,
            fmi.ethnowear.domain.model.document.processing.JobStatus status
    );

    @EntityGraph(attributePaths = {
            "documentPage",
            "documentPageMedia",
            "documentPageOcrResult",
            "processingJob"
    })
    Page<DocumentPageTextSuggestion> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );
}
