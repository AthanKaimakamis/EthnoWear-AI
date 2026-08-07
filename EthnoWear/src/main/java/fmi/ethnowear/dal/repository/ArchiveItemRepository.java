package fmi.ethnowear.dal.repository;

import fmi.ethnowear.application.enums.ArchiveType;
import fmi.ethnowear.application.enums.TrustedLevel;
import fmi.ethnowear.dal.entity.ArchiveItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {

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
}
