package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItemFeature;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface ArchiveItemFeatureRepository extends JpaRepository<ArchiveItemFeature, Long> {

    /**
     * Finds every feature observation belonging to one archive item, regardless of validation status.
     *
     * @param archiveItemId database identifier of the archive item
     * @return feature observations for the item, or an empty list when none exist
     */
    List<ArchiveItemFeature> findByArchiveItem_Id(Long archiveItemId);

    /**
     * Finds only validated feature observations belonging to one archive item.
     *
     * @param archiveItemId database identifier of the archive item
     * @return validated feature observations for the item, or an empty list when none exist
     */
    List<ArchiveItemFeature> findByArchiveItem_IdAndValidatedTrue(Long archiveItemId);

    /**
     * Finds validated feature observations for any of the supplied archive items in one query.
     * This bulk lookup avoids issuing a separate query for each archive item.
     *
     * @param archiveItemIds database identifiers of the archive items
     * @return validated feature observations for the supplied items, or an empty list when none exist
     */
    List<ArchiveItemFeature> findByArchiveItem_IdInAndValidatedTrue(Collection<Long> archiveItemIds);

    /**
     * Checks whether an archive item has at least one feature observation.
     *
     * @param archiveItemId database identifier of the archive item
     * @return {@code true} when at least one feature observation belongs to the item
     */
    boolean existsByArchiveItem_Id(Long archiveItemId);

    /**
     * Finds all feature observations of the specified type, regardless of validation status.
     *
     * @param featureType feature category used to filter the observations
     * @return matching feature observations, or an empty list when none exist
     */
    List<ArchiveItemFeature> findByFeatureType(FeatureType featureType);

    /**
     * Finds validated observations of a feature type by the feature's authoritative ontology IRI.
     *
     * @param featureType feature category used to filter the observations
     * @param ontologyIri complete ontology IRI of the observed feature
     * @return matching validated observations, or an empty list when none exist
     */
    @EntityGraph(attributePaths = { "sourceReference", "sourceReference.source" })
    List<ArchiveItemFeature> findByFeatureTypeAndOntologyIriAndValidatedTrue(FeatureType featureType, String ontologyIri);

    /**
     * Finds validated matching evidence for a page of archive items in one query.
     *
     * @param archiveItemIds database identifiers of the archive items
     * @param featureType ontology feature category being matched
     * @param ontologyIri authoritative ontology IRI of the entity
     * @return matching validated feature observations
     */
    @EntityGraph(attributePaths = "sourceReference")
    List<ArchiveItemFeature> findByArchiveItem_IdInAndFeatureTypeAndOntologyIriAndValidatedTrue(
            Collection<Long> archiveItemIds,
            FeatureType featureType,
            String ontologyIri
    );

    /**
     * Finds validated observations of a feature type by the feature's cached ontology local name.
     *
     * @param featureType feature category used to filter the observations
     * @param ontologyLocalName local name of the observed ontology resource
     * @return matching validated observations, or an empty list when none exist
     */
    List<ArchiveItemFeature> findByFeatureTypeAndOntologyLocalNameAndValidatedTrue(FeatureType featureType, String ontologyLocalName);

    /**
     * Finds every validated archive feature observation.
     *
     * @return all validated observations, or an empty list when none exist
     */
    List<ArchiveItemFeature> findByValidatedTrue();

    /**
     * Finds feature observations supported by the specified exact source reference.
     *
     * @param sourceReferenceId database identifier of the source reference
     * @return observations supported by the reference, or an empty list when none exist
     */
    List<ArchiveItemFeature> findBySourceReference_Id(Long sourceReferenceId);

    /**
     * Checks whether an exact source reference supports at least one feature observation.
     *
     * @param sourceReferenceId database identifier of the source reference
     * @return {@code true} when at least one feature observation uses the reference
     */
    boolean existsBySourceReference_Id(Long sourceReferenceId);

    /**
     * Checks whether an archive item contains an unvalidated feature.
     *
     * @param archiveItemId database identifier of the archive item
     * @return {@code true} when at least one feature remains unvalidated
     */
    boolean existsByArchiveItem_IdAndValidatedFalse(Long archiveItemId);
}
