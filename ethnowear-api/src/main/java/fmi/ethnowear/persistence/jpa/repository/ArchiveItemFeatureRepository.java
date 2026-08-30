package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import fmi.ethnowear.persistence.jpa.projection.OntologyEvidenceLinkProjection;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ArchiveItemFeatureRepository extends JpaRepository<ArchiveItemFeature, Long> {

    List<ArchiveItemFeature> findByArchiveItem_Id(
            Long archiveItemId
    );

    List<ArchiveItemFeature> findByArchiveItem_IdAndValidatedTrue(
            Long archiveItemId
    );

    List<ArchiveItemFeature> findByArchiveItem_IdInAndValidatedTrue(
            Collection<Long> archiveItemIds
    );

    boolean existsByArchiveItem_Id(
            Long archiveItemId
    );

    List<ArchiveItemFeature> findByFeatureType(
            FeatureType featureType
    );

    @EntityGraph(attributePaths = {"sourceReference", "sourceReference.source"})
    List<ArchiveItemFeature> findByFeatureTypeAndOntologyIriAndValidatedTrue(
            FeatureType featureType,
            String ontologyIri
    );

    @EntityGraph(attributePaths = "sourceReference")
    List<ArchiveItemFeature> findByArchiveItem_IdInAndFeatureTypeAndOntologyIriAndValidatedTrue(
            Collection<Long> archiveItemIds,
            FeatureType featureType,
            String ontologyIri
    );

    List<ArchiveItemFeature> findByFeatureTypeAndOntologyLocalNameAndValidatedTrue(
            FeatureType featureType,
            String ontologyLocalName
    );

    List<ArchiveItemFeature> findByValidatedTrue();

    List<ArchiveItemFeature> findBySourceReference_Id(
            Long sourceReferenceId
    );

    boolean existsBySourceReference_Id(
            Long sourceReferenceId
    );

    boolean existsByArchiveItem_IdAndValidatedFalse(
            Long archiveItemId
    );

    @Query("""
            SELECT DISTINCT feature.ontologyIri AS ontologyIri,
                            feature.archiveItem.id AS archiveItemId
            FROM ArchiveItemFeature feature
            WHERE feature.featureType = :featureType
                AND feature.ontologyIri IN :ontologyIris
                AND feature.validated = true
                AND feature.archiveItem.publicationStatus =
                    fmi.ethnowear.domain.model.archive.PublicationStatus.PUBLISHED
            """)
    List<OntologyEvidenceLinkProjection> findPublishedEvidenceLinks(
            @Param("featureType") FeatureType featureType,
            @Param("ontologyIris") Collection<String> ontologyIris
    );
}
