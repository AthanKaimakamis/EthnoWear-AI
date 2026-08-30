package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.Collection;
import java.util.List;

public interface DocumentPageOcrResultRepository extends JpaRepository<DocumentPageOcrResult, Long> {

    Page<DocumentPageOcrResult> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );

    Optional<DocumentPageOcrResult>
    findByDocumentPage_IdAndCurrentTrue(
            Long documentPageId
    );

    Optional<DocumentPageOcrResult> findByProcessingJob_Id(Long processingJobId);

    List<DocumentPageOcrResult> findByProcessingJob_IdIn(
            Collection<Long> processingJobIds
    );
}
