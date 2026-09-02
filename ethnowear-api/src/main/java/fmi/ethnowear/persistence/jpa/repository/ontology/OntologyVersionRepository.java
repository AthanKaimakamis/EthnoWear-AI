package fmi.ethnowear.persistence.jpa.repository.ontology;

import fmi.ethnowear.domain.model.ontology.OntologyVersionStatus;
import fmi.ethnowear.persistence.jpa.entity.ontology.OntologyVersion;
import fmi.ethnowear.persistence.jpa.projection.ontology.OntologyVersionSummaryProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OntologyVersionRepository extends JpaRepository<OntologyVersion, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT version FROM OntologyVersion version WHERE version.status = :status")
    Optional<OntologyVersion> findByStatusForUpdate(@Param("status") OntologyVersionStatus status);

    @Query("SELECT COALESCE(MAX(version.versionNumber), 0) FROM OntologyVersion version")
    long maximumVersionNumber();

    Optional<OntologyVersion> findByStatus(OntologyVersionStatus status);

    @Query("""
            SELECT version.id AS id,
                   version.versionNumber AS versionNumber,
                   version.createdAt AS createdAt,
                   actor.id AS createdByUserId,
                   actor.username AS createdByUsername,
                   version.changeReason AS changeReason,
                   version.contentHash AS contentHash,
                   version.fileName AS fileName,
                   version.ontologyNamespace AS ontologyNamespace,
                   version.valid AS valid,
                   version.validationMessage AS validationMessage,
                   previous.id AS previousVersionId,
                   previous.versionNumber AS previousVersionNumber,
                   restored.id AS restoredFromVersionId,
                   restored.versionNumber AS restoredFromVersionNumber,
                   version.status AS status
            FROM OntologyVersion version
            LEFT JOIN version.createdByUser actor
            LEFT JOIN version.previousVersion previous
            LEFT JOIN version.restoredFromVersion restored
            WHERE version.status = :status
            """)
    Optional<OntologyVersionSummaryProjection> findSummaryByStatus(
            @Param("status") OntologyVersionStatus status
    );

    @Query("""
            SELECT version.id AS id,
                   version.versionNumber AS versionNumber,
                   version.createdAt AS createdAt,
                   actor.id AS createdByUserId,
                   actor.username AS createdByUsername,
                   version.changeReason AS changeReason,
                   version.contentHash AS contentHash,
                   version.fileName AS fileName,
                   version.ontologyNamespace AS ontologyNamespace,
                   version.valid AS valid,
                   version.validationMessage AS validationMessage,
                   previous.id AS previousVersionId,
                   previous.versionNumber AS previousVersionNumber,
                   restored.id AS restoredFromVersionId,
                   restored.versionNumber AS restoredFromVersionNumber,
                   version.status AS status
            FROM OntologyVersion version
            LEFT JOIN version.createdByUser actor
            LEFT JOIN version.previousVersion previous
            LEFT JOIN version.restoredFromVersion restored
            WHERE version.id = :id
            """)
    Optional<OntologyVersionSummaryProjection> findSummaryById(@Param("id") Long id);

    @Query(
            value = """
                    SELECT version.id AS id,
                           version.versionNumber AS versionNumber,
                           version.createdAt AS createdAt,
                           actor.id AS createdByUserId,
                           actor.username AS createdByUsername,
                           version.changeReason AS changeReason,
                           version.contentHash AS contentHash,
                           version.fileName AS fileName,
                           version.ontologyNamespace AS ontologyNamespace,
                           version.valid AS valid,
                           version.validationMessage AS validationMessage,
                           previous.id AS previousVersionId,
                           previous.versionNumber AS previousVersionNumber,
                           restored.id AS restoredFromVersionId,
                           restored.versionNumber AS restoredFromVersionNumber,
                           version.status AS status
                    FROM OntologyVersion version
                    LEFT JOIN version.createdByUser actor
                    LEFT JOIN version.previousVersion previous
                    LEFT JOIN version.restoredFromVersion restored
                    """,
            countQuery = "SELECT COUNT(version) FROM OntologyVersion version"
    )
    Page<OntologyVersionSummaryProjection> findAllSummaries(Pageable pageable);
}
