package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DocumentPageOcrResultRepository extends JpaRepository<DocumentPageOcrResult, Long> {

    Page<DocumentPageOcrResult> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );

    Optional<DocumentPageOcrResult>
    findByDocumentPage_IdAndCurrentTrue(
            Long documentPageId
    );
}
