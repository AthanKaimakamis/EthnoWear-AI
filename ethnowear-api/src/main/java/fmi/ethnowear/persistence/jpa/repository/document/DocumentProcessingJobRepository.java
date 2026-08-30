package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.projection.document.ProcessingJobStatusCountProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface DocumentProcessingJobRepository extends JpaRepository<DocumentProcessingJob, Long> {

    @Query(
            value = """
                    SELECT job
                    FROM DocumentProcessingJob job
                    LEFT JOIN FETCH job.document directDocument
                    LEFT JOIN FETCH job.documentPage documentPage
                    LEFT JOIN FETCH documentPage.document pageDocument
                    WHERE job.jobKey IS NOT NULL
                    AND (:jobType IS NULL OR job.jobType = :jobType)
                    AND (:status IS NULL OR job.status = :status)
                    AND (
                        :documentId IS NULL
                        OR directDocument.id = :documentId
                        OR pageDocument.id = :documentId
                    )
                    AND (:documentPageId IS NULL OR documentPage.id = :documentPageId)
                    AND (
                        :searchPattern IS NULL
                        OR LOWER(directDocument.title) LIKE :searchPattern
                        OR LOWER(pageDocument.title) LIKE :searchPattern
                        OR LOWER(job.errorCode) LIKE :searchPattern
                        OR LOWER(job.safeErrorMessage) LIKE :searchPattern
                    )
                    """,
            countQuery = """
                    SELECT COUNT(job)
                    FROM DocumentProcessingJob job
                    LEFT JOIN job.document directDocument
                    LEFT JOIN job.documentPage documentPage
                    LEFT JOIN documentPage.document pageDocument
                    WHERE job.jobKey IS NOT NULL
                    AND (:jobType IS NULL OR job.jobType = :jobType)
                    AND (:status IS NULL OR job.status = :status)
                    AND (
                        :documentId IS NULL
                        OR directDocument.id = :documentId
                        OR pageDocument.id = :documentId
                    )
                    AND (:documentPageId IS NULL OR documentPage.id = :documentPageId)
                    AND (
                        :searchPattern IS NULL
                        OR LOWER(directDocument.title) LIKE :searchPattern
                        OR LOWER(pageDocument.title) LIKE :searchPattern
                        OR LOWER(job.errorCode) LIKE :searchPattern
                        OR LOWER(job.safeErrorMessage) LIKE :searchPattern
                    )
                    """
    )
    Page<DocumentProcessingJob> findAdminJobs(
            @Param("searchPattern") String searchPattern,
            @Param("jobType") JobType jobType,
            @Param("status") JobStatus status,
            @Param("documentId") Long documentId,
            @Param("documentPageId") Long documentPageId,
            Pageable pageable
    );

    @Query("""
        SELECT
            job.status AS status,
            COUNT(job) AS total
        FROM DocumentProcessingJob job
        LEFT JOIN job.document directDocument
        LEFT JOIN job.documentPage documentPage
        LEFT JOIN documentPage.document pageDocument
        WHERE job.jobKey IS NOT NULL
        AND (:jobType IS NULL OR job.jobType = :jobType)
        AND (
            :documentId IS NULL
            OR directDocument.id = :documentId
            OR pageDocument.id = :documentId
        )
        AND (:documentPageId IS NULL OR documentPage.id = :documentPageId)
        AND (
            :searchPattern IS NULL
            OR LOWER(directDocument.title) LIKE :searchPattern
            OR LOWER(pageDocument.title) LIKE :searchPattern
            OR LOWER(job.errorCode) LIKE :searchPattern
            OR LOWER(job.safeErrorMessage) LIKE :searchPattern
        )
        GROUP BY job.status
        """)
    java.util.List<ProcessingJobStatusCountProjection> countAdminJobsByStatus(
            @Param("searchPattern") String searchPattern,
            @Param("jobType") JobType jobType,
            @Param("documentId") Long documentId,
            @Param("documentPageId") Long documentPageId
    );

    @Query(
            value = """
                    SELECT job
                    FROM DocumentProcessingJob job
                    LEFT JOIN job.documentPage documentPage
                    WHERE job.jobKey IS NOT NULL
                    AND (
                        job.document.id = :documentId
                        OR documentPage.document.id = :documentId
                    )
                    ORDER BY
                        job.createdAt DESC,
                        job.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(job)
                    FROM DocumentProcessingJob job
                    LEFT JOIN job.documentPage documentPage
                    WHERE job.jobKey IS NOT NULL
                    AND (
                        job.document.id = :documentId
                        OR documentPage.document.id = :documentId
                    )
                    """
    )
    Page<DocumentProcessingJob> findDocumentHistory(
            @Param("documentId") Long documentId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT job
                    FROM DocumentProcessingJob job
                    WHERE job.document.id = :documentId
                    AND job.jobType = :jobType
                    ORDER BY job.createdAt DESC, job.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(job)
                    FROM DocumentProcessingJob job
                    WHERE job.document.id = :documentId
                    AND job.jobType = :jobType
                    """
    )
    Page<DocumentProcessingJob> findDocumentJobsByType(
            @Param("documentId") Long documentId,
            @Param("jobType") JobType jobType,
            Pageable pageable
    );

    Page<DocumentProcessingJob> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );

    Optional<DocumentProcessingJob> findByActiveJobKey(
            String activeJobKey
    );

    boolean existsByDocumentPage_IdAndActiveJobKeyIsNotNull(
            Long documentPageId
    );

    boolean existsByPreviousJob_Id(Long previousJobId);

    Optional<DocumentProcessingJob> findByJobKey(String jobKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentProcessingJob>
    findFirstByDocumentPage_IdAndJobTypeOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            JobType jobType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT job
        FROM DocumentProcessingJob job
        WHERE job.jobKey = :jobKey
        """)
    Optional<DocumentProcessingJob> findByJobKeyForUpdate(
            @Param("jobKey") String jobKey
    );

    Optional<DocumentProcessingJob> findByIdAndDocumentPage_Id(
            Long jobId,
            Long documentPageId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT job
        FROM DocumentProcessingJob job
        WHERE job.id = :jobId
        """)
    Optional<DocumentProcessingJob> findByIdForUpdate(
            @Param("jobId") Long jobId
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DocumentProcessingJob>
    findFirstByDocument_IdAndJobTypeOrderByCreatedAtDescIdDesc(
            Long documentId,
            JobType jobType
    );

    @Query("""
            SELECT CASE WHEN COUNT(job) > 0 THEN true ELSE false END
            FROM DocumentProcessingJob job
            LEFT JOIN job.documentPage page
            WHERE job.activeJobKey IS NOT NULL
            AND job.jobType <> :excludedJobType
            AND (
                job.document.id = :documentId
                OR page.document.id = :documentId
            )
            """)
    boolean existsActiveDocumentJobOtherThan(
            @Param("documentId") Long documentId,
            @Param("excludedJobType") JobType excludedJobType
    );

    boolean existsByInputMediaAsset_IdAndActiveJobKeyIsNotNullAndJobTypeNot(
            Long mediaAssetId,
            JobType excludedJobType
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT job
            FROM DocumentProcessingJob job
            WHERE job.jobType = :jobType
            AND job.status IN :statuses
            AND job.availableAt <= :now
            ORDER BY job.priority DESC, job.availableAt ASC, job.id ASC
            """)
    List<DocumentProcessingJob> findDueJobsForUpdate(
            @Param("jobType") JobType jobType,
            @Param("statuses") Collection<JobStatus> statuses,
            @Param("now") LocalDateTime now,
            Pageable pageable
    );

    List<DocumentProcessingJob> findByJobTypeAndUpdatedAtAndStatusIn(
            JobType jobType,
            LocalDateTime updatedAt,
            Collection<JobStatus> statuses
    );
}
