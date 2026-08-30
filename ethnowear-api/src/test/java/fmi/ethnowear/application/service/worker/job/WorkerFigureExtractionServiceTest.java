package fmi.ethnowear.application.service.worker.job;

import fmi.ethnowear.application.dto.archive.media.MediaAssetDetails;
import fmi.ethnowear.application.dto.worker.figure.WorkerFigureCropCommand;
import fmi.ethnowear.application.model.worker.WorkerClaimCredentials;
import fmi.ethnowear.application.service.archive.media.storage.MediaFileHasher;
import fmi.ethnowear.application.service.archive.media.storage.MediaUploadService;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobKeyFactory;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.application.service.worker.security.WorkerClaimedJobLoader;
import fmi.ethnowear.config.FigureExtractionProperties;
import fmi.ethnowear.domain.model.archive.MediaType;
import fmi.ethnowear.domain.model.document.processing.JobStatus;
import fmi.ethnowear.domain.model.document.processing.JobType;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigureCandidate;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureCandidateRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class WorkerFigureExtractionServiceTest {

    @Test
    void returnsExactContextAndMakesIdenticalCropRetryIdempotent() {
        Fixture fixture = fixture();
        WorkerFigureCropCommand command = new WorkerFigureCropCommand(
                51L,
                1,
                "1",
                "Шевица"
        );
        MockMultipartFile crop = new MockMultipartFile(
                "file",
                "figure.png",
                "image/png",
                "crop".getBytes()
        );

        var context = fixture.service().context(11L, fixture.credentials());
        var created = fixture.service().upload(11L, fixture.credentials(), command, crop);
        var repeated = fixture.service().upload(11L, fixture.credentials(), command, crop);

        assertEquals(61L, context.ocrResultId());
        assertEquals(1, context.candidates().size());
        assertFalse(created.existing());
        assertTrue(repeated.existing());
        assertEquals(71L, created.mediaAssetId());
        assertSame(fixture.sourceReference(), fixture.figure().get().getSourceReference());
        verify(fixture.uploadService(), times(1)).upload(any(), any(), any());
    }

    private Fixture fixture() {
        Document document = entity(new Document(), 7L);
        SourceReference source = entity(new SourceReference(), 3L);
        document.setDefaultSourceReference(source);
        DocumentPage page = entity(new DocumentPage(), 21L);
        page.setDocument(document);
        MediaAsset input = entity(new MediaAsset(), 31L);
        DocumentPageMedia pageMedia = entity(new DocumentPageMedia(), 41L);
        pageMedia.setDocumentPage(page);
        pageMedia.setMediaAsset(input);
        DocumentPageOcrResult result = entity(new DocumentPageOcrResult(), 61L);
        result.setDocumentPage(page);
        result.setDocumentPageMedia(pageMedia);
        result.setStructuredOutputJson("{\"words\":[]}");
        result.setCurrent(true);
        DocumentPageFigureCandidate candidate = entity(
                new DocumentPageFigureCandidate(),
                51L
        );
        candidate.setDocumentPageOcrResult(result);
        candidate.setDocumentPageMedia(pageMedia);
        candidate.setCandidateOrdinal(1);
        candidate.setNormalizedX(new BigDecimal("0.1000000"));
        candidate.setNormalizedY(new BigDecimal("0.1000000"));
        candidate.setNormalizedWidth(new BigDecimal("0.2000000"));
        candidate.setNormalizedHeight(new BigDecimal("0.2000000"));
        candidate.setDetectionConfidence(new BigDecimal("0.9000"));
        DocumentProcessingJob job = entity(new DocumentProcessingJob(), 11L);
        job.setJobType(JobType.EXTRACT_PAGE_FIGURES);
        job.setStatus(JobStatus.RUNNING);
        job.setDocumentPage(page);
        job.setInputMediaAsset(input);
        job.setAttemptCount(1);
        job.assignJobKey("EXTRACT_PAGE_FIGURES:PAGE:21:OCR_RESULT:61:JOB:test");

        WorkerClaimedJobLoader loader = mock(WorkerClaimedJobLoader.class);
        when(loader.requireActive(eq(11L), any())).thenReturn(job);
        DocumentPageOcrResultRepository ocrResults = mock(DocumentPageOcrResultRepository.class);
        when(ocrResults.findById(61L)).thenReturn(Optional.of(result));
        DocumentPageFigureCandidateRepository candidates =
                mock(DocumentPageFigureCandidateRepository.class);
        when(candidates.findByDocumentPageOcrResult_IdOrderByCandidateOrdinalAsc(61L))
                .thenReturn(List.of(candidate));
        when(candidates.findByIdAndDocumentPageOcrResult_Id(51L, 61L))
                .thenReturn(Optional.of(candidate));

        AtomicReference<DocumentPageFigure> stored = new AtomicReference<>();
        DocumentPageFigureRepository figures = mock(DocumentPageFigureRepository.class);
        when(figures.findByProcessingJob_IdAndProducingAttemptAndFigureOrdinal(11L, 1, 1))
                .thenAnswer(ignored -> Optional.ofNullable(stored.get()));
        when(figures.saveAndFlush(any())).thenAnswer(invocation -> {
            DocumentPageFigure figure = invocation.getArgument(0);
            EntityTestUtils.setId(figure, 81L);
            stored.set(figure);
            return figure;
        });

        String checksum = new MediaFileHasher().sha256(
                new java.io.ByteArrayInputStream("crop".getBytes())
        );
        MediaAsset cropMedia = entity(new MediaAsset(), 71L);
        cropMedia.setChecksum(checksum);
        MediaAssetRepository media = mock(MediaAssetRepository.class);
        when(media.findById(71L)).thenReturn(Optional.of(cropMedia));
        MediaUploadService upload = mock(MediaUploadService.class);
        when(upload.upload(any(), any(), any())).thenReturn(new MediaAssetDetails(
                71L, 3L, "figure.png", null, null, "image/png",
                MediaType.IMAGE, 100, 100, 4L, checksum, null, null,
                null, null, null
        ));

        WorkerFigureExtractionService service = new WorkerFigureExtractionService(
                loader,
                new DocumentProcessingJobKeyFactory(),
                ocrResults,
                candidates,
                figures,
                media,
                upload,
                new MediaFileHasher(),
                new FigureExtractionProperties(100, 2000, 100, DataSize.ofMegabytes(25)),
                mock(ManagementEventPublisher.class)
        );
        return new Fixture(service, upload, stored, source, new WorkerClaimCredentials("worker", "token"));
    }

    private <T extends AppendOnlyEntity> T entity(T entity, Long id) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }

    private record Fixture(
            WorkerFigureExtractionService service,
            MediaUploadService uploadService,
            AtomicReference<DocumentPageFigure> figure,
            SourceReference sourceReference,
            WorkerClaimCredentials credentials
    ) {
    }
}
