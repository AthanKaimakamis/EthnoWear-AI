package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageReview;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.Optional;
import java.util.Collection;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DocumentPageReviewRepository extends JpaRepository<DocumentPageReview, Long> {

    @EntityGraph(attributePaths = {
            "documentPage",
            "sourceTextSuggestion"
    })
    Page<DocumentPageReview> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );

    Optional<DocumentPageReview>
    findBySourceTextSuggestion_IdAndSourceTextSuggestionIssueOrdinalIsNull(
            Long sourceTextSuggestionId
    );

    Optional<DocumentPageReview>
    findBySourceTextSuggestion_IdAndSourceTextSuggestionIssueOrdinal(
            Long sourceTextSuggestionId,
            Integer sourceTextSuggestionIssueOrdinal
    );

    @Query("""
        SELECT review.sourceTextSuggestion.id
        FROM DocumentPageReview review
        WHERE review.sourceTextSuggestion.id IN :suggestionIds
        """)
    Set<Long> findAppliedSuggestionIds(
            @Param("suggestionIds") Collection<Long> suggestionIds
    );
}
