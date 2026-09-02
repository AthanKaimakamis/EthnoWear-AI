package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.persistence.jpa.repository.document.*;
import fmi.ethnowear.persistence.jpa.repository.ontology.OntologyVersionRepository;
import fmi.ethnowear.persistence.jdbc.document.DocumentDeletionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Import;
import fmi.ethnowear.domain.model.document.processing.JobType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@EnabledIfEnvironmentVariable(named = "ETHNOWEAR_LIVE_DB_TESTS", matches = "true")
@Import(DocumentDeletionStore.class)
class PersistenceSmokeTest {

    private static final String DEMO_CONTENT_HASH =
            "d579cb90978d1aeb8e104240d2d866f6ee83e883f941cb84afbd6106dcb68778";

    @Autowired
    private KnowledgeChunkRepository knowledgeChunkRepository;

    @Autowired
    private OntologyVersionRepository ontologyVersionRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPageRepository documentPageRepository;

    @Autowired
    private DocumentProcessingJobRepository documentProcessingJobRepository;

    @Autowired
    private DocumentPageMediaRepository documentPageMediaRepository;

    @Autowired
    private DocumentPageFigureCandidateRepository figureCandidateRepository;

    @Autowired
    private DocumentPageFigureRepository figureRepository;

    @Autowired
    private DocumentPageQualityAssessmentRepository qualityAssessmentRepository;

    @Autowired
    private DocumentPageQualitySignalRepository qualitySignalRepository;

    @Autowired
    private DocumentPageOcrResultRepository ocrResultRepository;

    @Autowired
    private DocumentPageReviewRepository reviewRepository;

    @Autowired
    private DocumentPageTextSuggestionRepository textSuggestionRepository;

    @Autowired
    private DocumentPageProvenanceEventRepository provenanceEventRepository;

    @Autowired
    private KnowledgeChunkPageRepository knowledgeChunkPageRepository;

    @Autowired
    private DocumentDeletionStore documentDeletionStore;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void readsExistingKnowledgeChunkWithCanonicalContentHash() {
        knowledgeChunkRepository.findById(1L).ifPresent(chunk ->
                assertThat(chunk.getContentHash()).isEqualTo(DEMO_CONTENT_HASH)
        );
    }

    @Test
    void readsAllStage3DocumentMappings() {
        var firstPage = PageRequest.of(0, 1);

        assertThat(documentRepository.findAll(firstPage)).isNotNull();
        assertThat(documentPageRepository.findAll(firstPage)).isNotNull();
        assertThat(documentProcessingJobRepository.findAll(firstPage)).isNotNull();
        assertThat(documentPageMediaRepository.findAll(firstPage)).isNotNull();
        assertThat(figureCandidateRepository.findAll(firstPage)).isNotNull();
        assertThat(figureRepository.findAll(firstPage)).isNotNull();
        assertThat(qualityAssessmentRepository.findAll(firstPage)).isNotNull();
        assertThat(qualitySignalRepository.findAll(firstPage)).isNotNull();
        assertThat(ocrResultRepository.findAll(firstPage)).isNotNull();
        assertThat(reviewRepository.findAll(firstPage)).isNotNull();
        assertThat(textSuggestionRepository.findAll(firstPage)).isNotNull();
        assertThat(provenanceEventRepository.findAll(firstPage)).isNotNull();
        assertThat(knowledgeChunkPageRepository.findAll(firstPage)).isNotNull();
    }

    @Test
    void readsOntologyVersionMapping() {
        assertThat(ontologyVersionRepository.findAll(PageRequest.of(0, 1))).isNotNull();
    }

    @Test
    void executesStage4DocumentQueryContracts() {
        var firstPage = PageRequest.of(0, 10);
        var documents = documentRepository.findDocuments(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                firstPage
        );

        assertThat(documents).isNotNull();

        if (documents.isEmpty())
            return;

        Long documentId = documents.getContent().getFirst().getId();
        assertThat(documentRepository.findDetailsById(documentId)).isPresent();
        assertThat(documentPageRepository.countProcessingStates(
                java.util.List.of(documentId)
        )).isNotNull();
        assertThat(documentPageRepository.countReviewStates(
                java.util.List.of(documentId)
        )).isNotNull();
        assertThat(documentPageRepository.countTranscriptionApprovalStates(
                java.util.List.of(documentId)
        )).isNotNull();
        assertThat(documentPageRepository.countIndexingStates(
                java.util.List.of(documentId)
        )).isNotNull();
        assertThat(documentProcessingJobRepository.findDocumentHistory(
                documentId,
                firstPage
        )).isNotNull();
        assertThat(documentProcessingJobRepository.findDocumentJobsByType(
                documentId,
                JobType.CHUNK_GENERATION,
                firstPage
        )).isNotNull();
        assertThat(knowledgeChunkRepository
                .findByDocument_IdAndGenerationInputHashIsNotNull(
                        documentId,
                        firstPage
                )).isNotNull();
        assertThat(documentPageRepository.findChunkGenerationCandidates(
                documentId
        )).isNotNull();

        var pages = documentPageRepository
                .findByDocument_IdOrderByPageSequenceAsc(
                        documentId,
                        firstPage
                );
        assertThat(pages).isNotNull();

        if (pages.isEmpty())
            return;

        Long pageId = pages.getContent().getFirst().getId();
        assertThat(documentPageRepository.findByIdAndDocument_Id(
                pageId,
                documentId
        )).isPresent();
        assertThat(documentPageMediaRepository.findPreviewCandidates(
                java.util.List.of(pageId)
        )).isNotNull();
        assertThat(ocrResultRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        firstPage
                )).isNotNull();
        assertThat(reviewRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        firstPage
                )).isNotNull();
        assertThat(provenanceEventRepository
                .findByDocumentPage_IdOrderByCreatedAtDescIdDesc(
                        pageId,
                        firstPage
                )).isNotNull();
        assertThat(qualityAssessmentRepository
                .findByDocumentPage_IdAndCurrentTrueOrderByAssessmentTypeAscIdAsc(
                        pageId
                )).isNotNull();
    }

    @Test
    void executesDocumentDeletionBatchAgainstSqlServer() {
        Long documentId = jdbc.queryForObject(
                """
                INSERT INTO ethnowear.Documents (
                    DocumentType,
                    ProvenanceStatus,
                    Title,
                    ProcessingState,
                    ReviewState,
                    ProvenanceTrustState,
                    IndexingState
                )
                OUTPUT INSERTED.Id
                VALUES (
                    N'PDF_DOCUMENT',
                    N'UNKNOWN_SOURCE',
                    N'Deletion smoke fixture',
                    N'UPLOADED',
                    N'NOT_READY',
                    N'UNKNOWN',
                    N'NOT_ELIGIBLE'
                )
                """,
                Long.class
        );

        assertThat(documentId).isNotNull();
        assertThat(documentDeletionStore.hasActiveJobs(documentId)).isFalse();
        assertThat(documentDeletionStore.delete(documentId)).isEmpty();
        assertThat(documentRepository.existsById(documentId)).isFalse();
    }

    @Test
    void enforcesOnePreferredOcrInputPerPage() {
        Long documentId = jdbc.queryForObject(
                """
                INSERT INTO ethnowear.Documents (
                    DocumentType, ProvenanceStatus, Title, ProcessingState,
                    ReviewState, ProvenanceTrustState, IndexingState
                )
                OUTPUT INSERTED.Id
                VALUES (
                    N'PDF_DOCUMENT', N'UNKNOWN_SOURCE', N'Preference constraint fixture',
                    N'UPLOADED', N'NOT_READY', N'UNKNOWN', N'NOT_ELIGIBLE'
                )
                """,
                Long.class
        );
        Long pageId = jdbc.queryForObject(
                """
                INSERT INTO ethnowear.DocumentPages (
                    DocumentId, PageKind, PageRole, PageSequence,
                    ProvenanceStatus, ProcessingState, ReviewState,
                    TranscriptionApprovalState, ProvenanceTrustState,
                    IndexingState, EvidenceState
                )
                OUTPUT INSERTED.Id
                VALUES (
                    ?, N'DOCUMENT_PAGE', N'NORMAL', 1,
                    N'UNKNOWN_SOURCE', N'UPLOADED', N'NOT_READY',
                    N'PENDING', N'UNKNOWN', N'NOT_ELIGIBLE', N'ACTIVE'
                )
                """,
                Long.class,
                documentId
        );
        Long firstMediaId = insertImageMedia();
        Long secondMediaId = insertImageMedia();

        jdbc.update(
                """
                INSERT INTO ethnowear.DocumentPageMedia (
                    DocumentPageId, MediaAssetId, RenditionType,
                    IsOriginal, IsPreferredOcrInput, DisplayOrder
                )
                VALUES (?, ?, N'PDF_PAGE_RENDER', 0, 1, 0)
                """,
                pageId,
                firstMediaId
        );

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO ethnowear.DocumentPageMedia (
                    DocumentPageId, MediaAssetId, RenditionType,
                    IsOriginal, IsPreferredOcrInput, DisplayOrder
                )
                VALUES (?, ?, N'PDF_PAGE_RENDER', 0, 1, 1)
                """,
                pageId,
                secondMediaId
        )).hasMessageContaining("UQ_DocumentPageMedia_PreferredOcrInput");
    }

    private Long insertImageMedia() {
        return jdbc.queryForObject(
                """
                INSERT INTO ethnowear.MediaAssets (MediaType)
                OUTPUT INSERTED.Id
                VALUES (N'IMAGE')
                """,
                Long.class
        );
    }
}
