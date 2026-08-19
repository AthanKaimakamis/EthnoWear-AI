package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageProvenanceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPageProvenanceEventRepository extends JpaRepository<DocumentPageProvenanceEvent, Long> {

    Page<DocumentPageProvenanceEvent> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );
}
