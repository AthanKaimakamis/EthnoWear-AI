package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.MediaFeatureAnnotationType;
import fmi.ethnowear.persistence.jpa.entity.MediaFeatureAnnotation;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MediaFeatureAnnotationRepository extends JpaRepository<MediaFeatureAnnotation, Long> {

    List<MediaFeatureAnnotation> findByArchiveItemMediaId(Long archiveItemMediaId);

    boolean existsByArchiveItemMedia_Id(Long archiveItemMediaId);

    List<MediaFeatureAnnotation> findByArchiveItemFeatureId(Long archiveItemFeatureId);

    boolean existsByArchiveItemFeature_Id(Long archiveItemFeatureId);

    List<MediaFeatureAnnotation> findByAnnotationType(MediaFeatureAnnotationType annotationType);

    @EntityGraph(attributePaths = {
            "archiveItemFeature",
            "archiveItemMedia.archiveItem",
            "archiveItemMedia.mediaAsset"
    })
    List<MediaFeatureAnnotation> findByArchiveItemFeature_FeatureTypeAndArchiveItemFeature_OntologyIriAndArchiveItemFeature_ValidatedTrueOrderByIdAsc(
            FeatureType featureType,
            String ontologyIri
    );

    @EntityGraph(attributePaths = {
            "archiveItemMedia",
            "archiveItemFeature"
    })
    List<MediaFeatureAnnotation> findByArchiveItemMedia_ArchiveItem_Id(Long archiveItemId);
}
