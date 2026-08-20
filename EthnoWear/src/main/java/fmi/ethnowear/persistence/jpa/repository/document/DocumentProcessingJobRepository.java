package fmi.ethnowear.persistence.jpa.repository.document;

import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DocumentProcessingJobRepository extends JpaRepository<DocumentProcessingJob, Long> {

    @Query(
            value = """
                    SELECT job
                    FROM DocumentProcessingJob job
                    LEFT JOIN job.documentPage documentPage
                    WHERE (
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
                    WHERE (
                        job.document.id = :documentId
                        OR documentPage.document.id = :documentId
                    )
                    """
    )
    Page<DocumentProcessingJob> findDocumentHistory(
            @Param("documentId") Long documentId,
            Pageable pageable
    );

    Page<DocumentProcessingJob> findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
            Long documentPageId,
            Pageable pageable
    );

    Optional<DocumentProcessingJob> findByActiveJobKey(
            String activeJobKey
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
}
