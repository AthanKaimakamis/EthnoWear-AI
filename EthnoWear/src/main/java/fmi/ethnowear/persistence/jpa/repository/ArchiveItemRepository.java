package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {

    /**
     * Finds archive records providing validated evidence for an ontology entity.
     * Region and regional-embroidery queries also include direct archive links.
     *
     * @param featureType                  ontology feature category being matched
     * @param ontologyIri                  authoritative ontology IRI of the entity
     * @param includeRegionDirectLinks     whether direct region links are included
     * @param includeEmbroideryDirectLinks whether direct regional-embroidery links are included
     * @param pageable                     page and sorting request
     * @return matching archive records without duplicates
     */
    @Query("""
            SELECT item
            FROM ArchiveItem item
            WHERE item.publicationStatus =
                fmi.ethnowear.domain.model.archive.PublicationStatus.PUBLISHED
                AND (
                    EXISTS (
                        SELECT feature.id
                        FROM ArchiveItemFeature feature
                        WHERE feature.archiveItem = item
                            AND feature.featureType = :featureType
                            AND feature.ontologyIri = :ontologyIri
                            AND feature.validated = true
                    )
                    OR (
                        :includeRegionDirectLinks = true
                        AND item.ontologyRegionIri = :ontologyIri
                    )
                    OR (
                        :includeEmbroideryDirectLinks = true
                        AND item.ontologyRegionalEmbroideryIri = :ontologyIri
                    )
                )
            """)
    Page<ArchiveItem> findOntologyEvidence(
            @Param("featureType") FeatureType featureType,
            @Param("ontologyIri") String ontologyIri,
            @Param("includeRegionDirectLinks") boolean includeRegionDirectLinks,
            @Param("includeEmbroideryDirectLinks") boolean includeEmbroideryDirectLinks,
            Pageable pageable
    );

    /**
     * Finds all archive items belonging to the specified archive type.
     *
     * @param archiveType archive category used to filter the items
     * @return matching archive items, or an empty list when none exist
     */
    List<ArchiveItem> findByArchiveType(ArchiveType archiveType);

    /**
     * Finds all archive items having the specified trust level.
     *
     * @param trustedLevel evidence trust level used to filter the items
     * @return matching archive items, or an empty list when none exist
     */
    List<ArchiveItem> findByTrustedLevel(TrustedLevel trustedLevel);

    /**
     * Finds archive items linked to a region by its authoritative ontology IRI.
     *
     * @param ontologyRegionIri complete ontology IRI of the region
     * @return archive items linked to the region, or an empty list when none exist
     */
    List<ArchiveItem> findByOntologyRegionIri(String ontologyRegionIri);

    /**
     * Finds archive items linked to a region by its cached ontology local name.
     *
     * @param ontologyRegionLocalName local name of the region ontology resource
     * @return archive items linked to the region, or an empty list when none exist
     */
    List<ArchiveItem> findByOntologyRegionLocalName(String ontologyRegionLocalName);

    /**
     * Finds archive items linked to a regional embroidery by its authoritative ontology IRI.
     *
     * @param ontologyRegionalEmbroideryIri complete ontology IRI of the regional embroidery
     * @return archive items linked to the regional embroidery, or an empty list when none exist
     */
    List<ArchiveItem> findByOntologyRegionalEmbroideryIri(String ontologyRegionalEmbroideryIri);

    /**
     * Finds archive items linked to a regional embroidery by its cached ontology local name.
     *
     * @param ontologyRegionalEmbroideryLocalName local name of the regional embroidery resource
     * @return archive items linked to the regional embroidery, or an empty list when none exist
     */
    List<ArchiveItem> findByOntologyRegionalEmbroideryLocalName(String ontologyRegionalEmbroideryLocalName);

    /**
     * Searches Bulgarian and English archive-item titles without case sensitivity.
     * Pass the same search text to both parameters to search both languages equally.
     *
     * @param titleBg text searched within Bulgarian titles
     * @param titleEn text searched within English titles
     * @return archive items matching either title, or an empty list when none exist
     */
    List<ArchiveItem> findByTitleBgContainingIgnoreCaseOrTitleEnContainingIgnoreCase(String titleBg, String titleEn);

    /**
     * Finds an archive item and eagerly loads its source citation.
     *
     * @param id database identifier of the archive item
     * @return matching archive item, or empty when it does not exist
     */
    @EntityGraph(attributePaths = {
            "sourceReference",
            "sourceReference.source"
    })
    Optional<ArchiveItem> findOneById(Long id);

    /**
     * Checks whether an exact source reference supports at least one archive item.
     *
     * @param sourceReferenceId database identifier of the source reference
     * @return {@code true} when at least one archive item uses the reference
     */
    boolean existsBySourceReference_Id(Long sourceReferenceId);

    @EntityGraph(attributePaths = {
            "sourceReference",
            "sourceReference.source"
    })
    @Query("""
        SELECT item
        FROM ArchiveItem item
        WHERE item.id = :id
            AND item.publicationStatus =
                fmi.ethnowear.domain.model.archive.PublicationStatus.PUBLISHED
        """)
    Optional<ArchiveItem> findPublishedById(@Param("id") Long id);
}
