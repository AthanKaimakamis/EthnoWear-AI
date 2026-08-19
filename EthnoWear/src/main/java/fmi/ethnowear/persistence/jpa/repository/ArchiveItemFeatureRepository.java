package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
