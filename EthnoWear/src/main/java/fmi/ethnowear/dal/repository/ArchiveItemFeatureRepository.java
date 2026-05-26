package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.FeatureType;
import fmi.ethnowear.dal.entity.ArchiveItemFeature;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveItemFeatureRepository extends JpaRepository<ArchiveItemFeature, Long> {

    List<ArchiveItemFeature> findByArchiveItemId(Long archiveItemId);

    List<ArchiveItemFeature> findByFeatureType(FeatureType featureType);

    List<ArchiveItemFeature> findByFeatureTypeAndOntologyLocalName(FeatureType featureType, String ontologyLocalName);

    List<ArchiveItemFeature> findByValidatedTrue();

    List<ArchiveItemFeature> findBySourceReferenceId(Long sourceReferenceId);
}
