package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DocumentPageFigureRepository
        extends JpaRepository<DocumentPageFigure, Long> {

    @EntityGraph(attributePaths = {
            "documentPage",
            "documentPage.document",
            "documentPageMedia",
            "mediaAsset",
            "sourceReference",
            "processingJob",
            "figureCandidate"
    })
    List<DocumentPageFigure>
    findByDocumentPage_IdOrderByFigureOrdinalAscIdAsc(Long pageId);

    @EntityGraph(attributePaths = {
            "documentPage",
            "documentPage.document",
            "documentPageMedia",
            "mediaAsset",
            "sourceReference",
            "processingJob",
            "figureCandidate"
    })
    Optional<DocumentPageFigure> findByIdAndDocumentPage_Id(Long id, Long pageId);

    Optional<DocumentPageFigure>
    findByProcessingJob_IdAndProducingAttemptAndFigureOrdinal(
            Long jobId,
            Integer attempt,
            Integer figureOrdinal
    );

    List<DocumentPageFigure>
    findByProcessingJob_IdAndProducingAttemptOrderByFigureOrdinalAsc(
            Long jobId,
            Integer attempt
    );

    List<DocumentPageFigure>
    findByDocumentPage_IdAndReviewStateIn(
            Long pageId,
            Collection<FigureReviewState> states
    );

    boolean existsByMediaAsset_Id(Long mediaAssetId);

    boolean existsByMediaAsset_IdAndReviewState(
            Long mediaAssetId,
            FigureReviewState reviewState
    );

    @EntityGraph(attributePaths = {
            "documentPage",
            "documentPage.document",
            "mediaAsset",
            "sourceReference"
    })
    List<DocumentPageFigure> findByMediaAsset_IdInAndReviewState(
            Collection<Long> mediaAssetIds,
            FigureReviewState reviewState
    );

    @EntityGraph(attributePaths = {
            "documentPage",
            "documentPage.document",
            "mediaAsset",
            "sourceReference"
    })
    Optional<DocumentPageFigure> findFirstByMediaAsset_IdAndReviewStateOrderByIdAsc(
            Long mediaAssetId,
            FigureReviewState reviewState
    );

    boolean existsBySourceReference_Id(Long sourceReferenceId);
}
