package fmi.ethnowear.persistence.jdbc.document;

import org.jspecify.annotations.NonNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class DocumentDeletionStore {

    private static final String ACTIVE_JOB_SQL = """
            SELECT COUNT_BIG(*)
            FROM ethnowear.DocumentProcessingJobs job
            LEFT JOIN ethnowear.DocumentPages page
                ON page.Id = job.DocumentPageId
            WHERE (
                job.DocumentId = ?
                OR page.DocumentId = ?
            )
            AND (
                job.ActiveJobKey IS NOT NULL
                OR job.Status IN (
                    N'QUEUED',
                    N'CLAIMED',
                    N'RUNNING',
                    N'RETRY_WAIT',
                    N'CANCEL_REQUESTED'
                )
            )
            """;

    private static final String INDEXED_CHUNK_IDS_SQL = """
            SELECT DISTINCT chunk.Id
            FROM ethnowear.KnowledgeChunks chunk
            LEFT JOIN ethnowear.KnowledgeChunkPages chunkPage
                ON chunkPage.KnowledgeChunkId = chunk.Id
            LEFT JOIN ethnowear.DocumentPages page
                ON page.Id = chunkPage.DocumentPageId
            WHERE (
                chunk.DocumentId = ?
                OR page.DocumentId = ?
            )
            AND chunk.VectorPointId IS NOT NULL
            ORDER BY chunk.Id
            """;

    private static final String DELETE_SQL = """
            SET NOCOUNT ON;

            DECLARE @DocumentId BIGINT = ?;

            DECLARE @PageIds TABLE (
                Id BIGINT NOT NULL PRIMARY KEY
            );
            DECLARE @ChunkIds TABLE (
                Id BIGINT NOT NULL PRIMARY KEY
            );
            DECLARE @JobIds TABLE (
                Id BIGINT NOT NULL PRIMARY KEY
            );
            DECLARE @PageMediaIds TABLE (
                Id BIGINT NOT NULL PRIMARY KEY
            );
            DECLARE @MediaIds TABLE (
                Id BIGINT NOT NULL PRIMARY KEY
            );

            INSERT INTO @PageIds (Id)
            SELECT page.Id
            FROM ethnowear.DocumentPages page
            WHERE page.DocumentId = @DocumentId;

            INSERT INTO @ChunkIds (Id)
            SELECT DISTINCT chunk.Id
            FROM ethnowear.KnowledgeChunks chunk
            LEFT JOIN ethnowear.KnowledgeChunkPages chunkPage
                ON chunkPage.KnowledgeChunkId = chunk.Id
            LEFT JOIN @PageIds page
                ON page.Id = chunkPage.DocumentPageId
            WHERE chunk.DocumentId = @DocumentId
               OR page.Id IS NOT NULL;

            ;WITH TargetJobs AS (
                SELECT job.Id
                FROM ethnowear.DocumentProcessingJobs job
                LEFT JOIN @PageIds page
                    ON page.Id = job.DocumentPageId
                LEFT JOIN @ChunkIds chunk
                    ON chunk.Id = job.KnowledgeChunkId
                WHERE job.DocumentId = @DocumentId
                   OR page.Id IS NOT NULL
                   OR chunk.Id IS NOT NULL

                UNION ALL

                SELECT child.Id
                FROM ethnowear.DocumentProcessingJobs child
                JOIN TargetJobs parent
                    ON parent.Id = child.PreviousJobId
            )
            INSERT INTO @JobIds (Id)
            SELECT DISTINCT Id
            FROM TargetJobs
            OPTION (MAXRECURSION 32767);

            INSERT INTO @PageMediaIds (Id)
            SELECT pageMedia.Id
            FROM ethnowear.DocumentPageMedia pageMedia
            JOIN @PageIds page
                ON page.Id = pageMedia.DocumentPageId;

            INSERT INTO @MediaIds (Id)
            SELECT document.OriginalMediaAssetId
            FROM ethnowear.Documents document
            WHERE document.Id = @DocumentId
              AND document.OriginalMediaAssetId IS NOT NULL

            UNION

            SELECT document.ThumbnailMediaAssetId
            FROM ethnowear.Documents document
            WHERE document.Id = @DocumentId
              AND document.ThumbnailMediaAssetId IS NOT NULL

            UNION

            SELECT pageMedia.MediaAssetId
            FROM ethnowear.DocumentPageMedia pageMedia
            JOIN @PageMediaIds target
                ON target.Id = pageMedia.Id

            UNION

            SELECT job.InputMediaAssetId
            FROM ethnowear.DocumentProcessingJobs job
            JOIN @JobIds target
                ON target.Id = job.Id
            WHERE job.InputMediaAssetId IS NOT NULL

            UNION

            SELECT figure.MediaAssetId
            FROM ethnowear.DocumentPageFigures figure
            JOIN @PageIds page
                ON page.Id = figure.DocumentPageId;

            IF EXISTS (
                SELECT 1
                FROM ethnowear.Documents document
                WHERE document.MergedIntoDocumentId = @DocumentId
                  AND document.Id <> @DocumentId
            )
                THROW 51001, 'Another document is merged into this document', 1;

            IF EXISTS (
                SELECT 1
                FROM ethnowear.DocumentPages page
                JOIN @PageIds target
                    ON target.Id = page.CanonicalDocumentPageId
                WHERE page.DocumentId <> @DocumentId
            )
                THROW 51001, 'Another document page links to this document', 1;

            IF EXISTS (
                SELECT 1
                FROM ethnowear.KnowledgeChunkPages chunkPage
                JOIN @ChunkIds chunk
                    ON chunk.Id = chunkPage.KnowledgeChunkId
                JOIN ethnowear.DocumentPages page
                    ON page.Id = chunkPage.DocumentPageId
                WHERE page.DocumentId <> @DocumentId
            )
                THROW 51001, 'A knowledge chunk also cites another document', 1;

            UPDATE page
            SET CurrentQualityAssessmentId = NULL
            FROM ethnowear.DocumentPages page
            JOIN @PageIds target
                ON target.Id = page.Id;

            DELETE signal
            FROM ethnowear.DocumentPageQualitySignals signal
            JOIN ethnowear.DocumentPageQualityAssessments assessment
                ON assessment.Id = signal.AssessmentId
            JOIN @PageIds page
                ON page.Id = assessment.DocumentPageId;

            DELETE assessment
            FROM ethnowear.DocumentPageQualityAssessments assessment
            JOIN @PageIds page
                ON page.Id = assessment.DocumentPageId;

            DELETE review
            FROM ethnowear.DocumentPageReviews review
            JOIN @PageIds page
                ON page.Id = review.DocumentPageId;

            DELETE suggestion
            FROM ethnowear.DocumentPageTextSuggestions suggestion
            JOIN @PageIds page
                ON page.Id = suggestion.DocumentPageId;

            DELETE provenance
            FROM ethnowear.DocumentPageProvenanceEvents provenance
            JOIN @PageIds page
                ON page.Id = provenance.DocumentPageId;

            DELETE figure
            FROM ethnowear.DocumentPageFigures figure
            JOIN @PageIds page
                ON page.Id = figure.DocumentPageId;

            DELETE candidate
            FROM ethnowear.DocumentPageFigureCandidates candidate
            JOIN ethnowear.DocumentPageOcrResults ocr
                ON ocr.Id = candidate.DocumentPageOcrResultId
            JOIN @PageIds page
                ON page.Id = ocr.DocumentPageId;

            DELETE ocr
            FROM ethnowear.DocumentPageOcrResults ocr
            JOIN @PageIds page
                ON page.Id = ocr.DocumentPageId;

            DELETE chunkPage
            FROM ethnowear.KnowledgeChunkPages chunkPage
            JOIN @ChunkIds chunk
                ON chunk.Id = chunkPage.KnowledgeChunkId;

            DELETE attempt
            FROM ethnowear.DocumentProcessingJobAttempts attempt
            JOIN @JobIds job
                ON job.Id = attempt.ProcessingJobId;

            UPDATE pageMedia
            SET DerivativeOfDocumentPageMediaId = NULL
            FROM ethnowear.DocumentPageMedia pageMedia
            JOIN @PageMediaIds target
                ON target.Id = pageMedia.Id;

            DELETE pageMedia
            FROM ethnowear.DocumentPageMedia pageMedia
            JOIN @PageMediaIds target
                ON target.Id = pageMedia.Id;

            UPDATE job
            SET PreviousJobId = NULL
            FROM ethnowear.DocumentProcessingJobs job
            JOIN @JobIds target
                ON target.Id = job.Id;

            DELETE job
            FROM ethnowear.DocumentProcessingJobs job
            JOIN @JobIds target
                ON target.Id = job.Id;

            UPDATE chunk
            SET SupersededByKnowledgeChunkId = NULL
            FROM ethnowear.KnowledgeChunks chunk
            JOIN @ChunkIds target
                ON target.Id = chunk.Id;

            DELETE chunk
            FROM ethnowear.KnowledgeChunks chunk
            JOIN @ChunkIds target
                ON target.Id = chunk.Id;

            UPDATE page
            SET CanonicalDocumentPageId = NULL
            FROM ethnowear.DocumentPages page
            JOIN @PageIds target
                ON target.Id = page.Id;

            DELETE page
            FROM ethnowear.DocumentPages page
            JOIN @PageIds target
                ON target.Id = page.Id;

            DELETE FROM ethnowear.Documents
            WHERE Id = @DocumentId;

            DELETE media
            OUTPUT
                DELETED.FilePath,
                DELETED.ThumbnailPath
            FROM ethnowear.MediaAssets media
            JOIN @MediaIds target
                ON target.Id = media.Id
            WHERE NOT EXISTS (
                SELECT 1
                FROM ethnowear.Documents document
                WHERE document.OriginalMediaAssetId = media.Id
                   OR document.ThumbnailMediaAssetId = media.Id
            )
            AND NOT EXISTS (
                SELECT 1
                FROM ethnowear.DocumentPageMedia pageMedia
                WHERE pageMedia.MediaAssetId = media.Id
            )
            AND NOT EXISTS (
                SELECT 1
                FROM ethnowear.DocumentProcessingJobs job
                WHERE job.InputMediaAssetId = media.Id
            )
            AND NOT EXISTS (
                SELECT 1
                FROM ethnowear.DocumentPageFigures figure
                WHERE figure.MediaAssetId = media.Id
            )
            AND NOT EXISTS (
                SELECT 1
                FROM ethnowear.ArchiveItemMedia archiveMedia
                WHERE archiveMedia.MediaAssetId = media.Id
            )
            AND NOT EXISTS (
                SELECT 1
                FROM ethnowear.MediaEntityLinks entityLink
                WHERE entityLink.MediaAssetId = media.Id
            )
            AND NOT EXISTS (
                SELECT 1
                FROM ethnowear.Sources source
                WHERE source.FilePath = media.FilePath
                  AND media.FilePath IS NOT NULL
            );
            """;

    private final JdbcTemplate jdbc;

    public DocumentDeletionStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasActiveJobs(Long documentId) {
        Long count = jdbc.queryForObject(
                ACTIVE_JOB_SQL,
                Long.class,
                documentId,
                documentId
        );

        return count != null && count > 0;
    }

    public List<DeletedMediaStorageKeys> delete(Long documentId) {
        return jdbc.query(
                DELETE_SQL,
                (statement) -> statement.setLong(1, documentId),
                (result, ignored) -> new DeletedMediaStorageKeys(
                        result.getString("FilePath"),
                        result.getString("ThumbnailPath")
                )
        );
    }

    public List<Long> findIndexedKnowledgeChunkIds(Long documentId) {
        return jdbc.queryForList(
                INDEXED_CHUNK_IDS_SQL,
                Long.class,
                documentId,
                documentId
        );
    }

    public record DeletedMediaStorageKeys(
            String filePath,
            String thumbnailPath
    ) {
    }
}
