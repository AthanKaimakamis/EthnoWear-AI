package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.domain.model.archive.ArchiveType;
import fmi.ethnowear.domain.model.ontology.FeatureType;
import fmi.ethnowear.domain.model.archive.TrustedLevel;
import fmi.ethnowear.persistence.jpa.entity.ArchiveItem;
import fmi.ethnowear.persistence.jpa.projection.OntologyEvidenceLinkProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ArchiveItemRepository extends JpaRepository<ArchiveItem, Long> {

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

    List<ArchiveItem> findByArchiveType(ArchiveType archiveType);

    List<ArchiveItem> findByTrustedLevel(TrustedLevel trustedLevel);

    List<ArchiveItem> findByOntologyRegionIri(String ontologyRegionIri);

    List<ArchiveItem> findByOntologyRegionLocalName(String ontologyRegionLocalName);

    List<ArchiveItem> findByOntologyRegionalEmbroideryIri(String ontologyRegionalEmbroideryIri);

    List<ArchiveItem> findByOntologyRegionalEmbroideryLocalName(String ontologyRegionalEmbroideryLocalName);

    List<ArchiveItem> findByTitleBgContainingIgnoreCaseOrTitleEnContainingIgnoreCase(String titleBg, String titleEn);

    @EntityGraph(attributePaths = {
            "sourceReference",
            "sourceReference.source"
    })
    Optional<ArchiveItem> findOneById(Long id);

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

    @Query("""
            SELECT item.ontologyRegionIri AS ontologyIri,
                   item.id AS archiveItemId
            FROM ArchiveItem item
            WHERE item.ontologyRegionIri IN :ontologyIris
                AND item.publicationStatus =
                    fmi.ethnowear.domain.model.archive.PublicationStatus.PUBLISHED
            """)
    List<OntologyEvidenceLinkProjection> findPublishedRegionEvidenceLinks(
            @Param("ontologyIris") List<String> ontologyIris
    );

    @Query("""
            SELECT item.ontologyRegionalEmbroideryIri AS ontologyIri,
                   item.id AS archiveItemId
            FROM ArchiveItem item
            WHERE item.ontologyRegionalEmbroideryIri IN :ontologyIris
                AND item.publicationStatus =
                    fmi.ethnowear.domain.model.archive.PublicationStatus.PUBLISHED
            """)
    List<OntologyEvidenceLinkProjection> findPublishedRegionalEmbroideryEvidenceLinks(
            @Param("ontologyIris") List<String> ontologyIris
    );
}
