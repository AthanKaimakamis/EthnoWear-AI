package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentPageReviewRepository extends JpaRepository<DocumentPageReview, Long> {

    Page<DocumentPageReview> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );
}
