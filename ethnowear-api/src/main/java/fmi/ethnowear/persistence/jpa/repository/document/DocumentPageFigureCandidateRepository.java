package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigureCandidate;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentPageFigureCandidateRepository
        extends JpaRepository<DocumentPageFigureCandidate, Long> {

    List<DocumentPageFigureCandidate>
    findByDocumentPageOcrResult_IdOrderByCandidateOrdinalAsc(Long ocrResultId);

    long countByDocumentPageOcrResult_Id(Long ocrResultId);

    @EntityGraph(attributePaths = {
            "documentPageOcrResult",
            "documentPageOcrResult.documentPage",
            "documentPageMedia",
            "documentPageMedia.mediaAsset"
    })
    Optional<DocumentPageFigureCandidate>
    findByIdAndDocumentPageOcrResult_Id(Long id, Long ocrResultId);
}
