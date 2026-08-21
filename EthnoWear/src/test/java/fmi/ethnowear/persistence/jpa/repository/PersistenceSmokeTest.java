package fmi.ethnowear.persistence.jpa.repository;

import fmi.ethnowear.persistence.jpa.repository.document.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE;

@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@EnabledIfEnvironmentVariable(named = "ETHNOWEAR_LIVE_DB_TESTS", matches = "true")
class PersistenceSmokeTest {

    private static final String DEMO_CONTENT_HASH =
            "d579cb90978d1aeb8e104240d2d866f6ee83e883f941cb84afbd6106dcb68778";

    @Autowired
    private KnowledgeChunkRepository knowledgeChunkRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentPageRepository documentPageRepository;

    @Autowired
    private DocumentProcessingJobRepository documentProcessingJobRepository;

    @Autowired
    private DocumentPageMediaRepository documentPageMediaRepository;

    @Autowired
    private DocumentPageQualityAssessmentRepository qualityAssessmentRepository;

    @Autowired
    private DocumentPageQualitySignalRepository qualitySignalRepository;

    @Autowired
    private DocumentPageOcrResultRepository ocrResultRepository;

    @Autowired
    private DocumentPageReviewRepository reviewRepository;

    @Autowired
    private DocumentPageProvenanceEventRepository provenanceEventRepository;

    @Autowired
    private KnowledgeChunkPageRepository knowledgeChunkPageRepository;

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
        assertThat(qualityAssessmentRepository.findAll(firstPage)).isNotNull();
        assertThat(qualitySignalRepository.findAll(firstPage)).isNotNull();
        assertThat(ocrResultRepository.findAll(firstPage)).isNotNull();
        assertThat(reviewRepository.findAll(firstPage)).isNotNull();
        assertThat(provenanceEventRepository.findAll(firstPage)).isNotNull();
        assertThat(knowledgeChunkPageRepository.findAll(firstPage)).isNotNull();
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
}
