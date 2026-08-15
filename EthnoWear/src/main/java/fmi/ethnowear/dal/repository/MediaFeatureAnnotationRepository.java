package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.application.enums.MediaFeatureAnnotationType;
import fmi.ethnowear.dal.entity.MediaFeatureAnnotation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaFeatureAnnotationRepository extends JpaRepository<MediaFeatureAnnotation, Long> {

    /**
     * Finds feature annotations belonging to one archive-item media link.
     *
     * @param archiveItemMediaId database identifier of the archive-item media link
     * @return matching annotations, or an empty list when none exist
     */
    List<MediaFeatureAnnotation> findByArchiveItemMediaId(Long archiveItemMediaId);

    /**
     * Checks whether an archive-item media link has at least one feature annotation.
     *
     * @param archiveItemMediaId database identifier of the archive-item media link
     * @return {@code true} when at least one annotation belongs to the media link
     */
    boolean existsByArchiveItemMedia_Id(Long archiveItemMediaId);

    /**
     * Finds media annotations referring to one archive feature observation.
     *
     * @param archiveItemFeatureId database identifier of the archive feature observation
     * @return matching annotations, or an empty list when none exist
     */
    List<MediaFeatureAnnotation> findByArchiveItemFeatureId(Long archiveItemFeatureId);

    /**
     * Checks whether an archive feature observation has at least one media annotation.
     *
     * @param archiveItemFeatureId database identifier of the archive feature observation
     * @return {@code true} when at least one annotation refers to the feature observation
     */
    boolean existsByArchiveItemFeature_Id(Long archiveItemFeatureId);

    /**
     * Finds media feature annotations of the specified annotation type.
     *
     * @param annotationType annotation category used to filter the records
     * @return matching annotations, or an empty list when none exist
     */
    List<MediaFeatureAnnotation> findByAnnotationType(MediaFeatureAnnotationType annotationType);

    /**
     * Finds media annotations attached to validated observations of an ontology entity.
     * Media assets and archive items are loaded with the annotations.
     *
     * @param featureType ontology feature category
     * @param ontologyIri authoritative ontology IRI of the entity
     * @return matching media annotations ordered by database identifier
     */
    @EntityGraph(attributePaths = {"archiveItemFeature", "archiveItemMedia.archiveItem", "archiveItemMedia.mediaAsset"})
    List<MediaFeatureAnnotation> findByArchiveItemFeature_FeatureTypeAndArchiveItemFeature_OntologyIriAndArchiveItemFeature_ValidatedTrueOrderByIdAsc(FeatureType featureType, String ontologyIri);

    /**
     * Finds all media annotations belonging to one archive item.
     *
     * @param archiveItemId database identifier of the archive item
     * @return annotations belonging to the archive item's media
     */
    @EntityGraph(attributePaths = {
            "archiveItemMedia",
            "archiveItemFeature"
    })
    List<MediaFeatureAnnotation> findByArchiveItemMedia_ArchiveItem_Id(Long archiveItemId);
}
