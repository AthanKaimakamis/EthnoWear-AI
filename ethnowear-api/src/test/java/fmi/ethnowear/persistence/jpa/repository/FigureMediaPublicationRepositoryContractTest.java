package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.assertTrue;

class FigureMediaPublicationRepositoryContractTest {

    @Test
    void generalLibraryQueryPublishesOnlyApprovedGeneratedFigures() throws Exception {
        Query query = MediaAssetRepository.class
                .getMethod(
                        "findVisibleInGeneralLibrary",
                        MediaOrigin.class,
                        FigureReviewState.class,
                        Pageable.class
                )
                .getAnnotation(Query.class);

        String jpql = query.value();
        assertTrue(jpql.contains("asset.origin <> :generatedOrigin"));
        assertTrue(jpql.contains("figure.reviewState = :approvedState"));
    }

    @Test
    void publicDeliveryQueryHidesEveryUnapprovedFigureState() throws Exception {
        Query query = MediaAssetRepository.class
                .getMethod(
                        "findPubliclyDeliverableById",
                        Long.class,
                        FigureReviewState.class
                )
                .getAnnotation(Query.class);

        String jpql = query.value();
        assertTrue(jpql.contains("NOT EXISTS"));
        assertTrue(jpql.contains("figure.reviewState = :approvedState"));
    }
}
