package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.media.MediaOrigin;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;

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
        assertTrue(jpql.contains("asset.origin IS NULL"));
        assertTrue(jpql.contains("asset.origin <> :generatedOrigin"));
        assertTrue(jpql.contains("figure.reviewState = :approvedState"));
    }

    @Test
    void legacyMediaWithoutAnOriginRemainsManageable() throws Exception {
        Query query = MediaAssetRepository.class
                .getMethod(
                        "findVisibleInGeneralLibraryById",
                        Long.class,
                        MediaOrigin.class,
                        FigureReviewState.class
                )
                .getAnnotation(Query.class);

        assertTrue(query.value().contains("asset.origin IS NULL"));
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
        assertTrue(jpql.contains("asset.publicDisplayAllowed = true"));
        assertTrue(jpql.contains("LEFT JOIN asset.sourceReference sourceReference"));
        assertTrue(jpql.contains("LEFT JOIN sourceReference.source source"));
        assertTrue(jpql.contains("sourceReference IS NULL"));
        assertTrue(jpql.contains("source.publicDisplayAllowed = true"));
    }

    @Test
    void publicRepresentativeMediaKeepsRightsClearedAssetsWithoutSources() throws Exception {
        Query query = ArchiveItemMediaRepository.class
                .getMethod(
                        "findPublicByArchiveItemIdsRolesAndMediaType",
                        Collection.class,
                        Collection.class,
                        MediaType.class,
                        FigureReviewState.class
                )
                .getAnnotation(Query.class);

        String jpql = query.value();
        assertTrue(jpql.contains("LEFT JOIN itemMedia.mediaAsset.sourceReference sourceReference"));
        assertTrue(jpql.contains("LEFT JOIN sourceReference.source source"));
        assertTrue(jpql.contains("sourceReference IS NULL"));
        assertTrue(jpql.contains("source.publicDisplayAllowed = true"));
    }
}
