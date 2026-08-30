package fmi.ethnowear.application.service.document.figure;

import fmi.ethnowear.application.dto.document.command.figure.FigureCaptionUpdateCommand;
import fmi.ethnowear.application.dto.document.command.figure.FigureReviewCommand;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.entity.MediaAsset;
import fmi.ethnowear.persistence.jpa.entity.SourceReference;
import fmi.ethnowear.persistence.jpa.entity.document.Document;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPage;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigureCandidate;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageMedia;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.MediaAssetRepository;
import fmi.ethnowear.persistence.jpa.repository.SourceReferenceRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureCandidateRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageRepository;
import fmi.ethnowear.testutil.EntityTestUtils;
import fmi.ethnowear.util.RowVersionUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentPageFigurePublicationTest {

    private static final byte[] VERSION = {1, 2, 3, 4};

    private final DocumentPageRepository pages = mock(DocumentPageRepository.class);
    private final DocumentPageFigureRepository figures = mock(DocumentPageFigureRepository.class);
    private final SourceReferenceRepository references = mock(SourceReferenceRepository.class);
    private final ManagementEventPublisher events = mock(ManagementEventPublisher.class);

    private DocumentPageFigure figure;
    private DocumentPageFigureAdminService service;

    @BeforeEach
    void setUp() {
        figure = figure();
        when(figures.findByIdAndDocumentPage_Id(31L, 21L))
                .thenReturn(Optional.of(figure));
        when(figures.saveAndFlush(any(DocumentPageFigure.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service = new DocumentPageFigureAdminService(
                pages,
                figures,
                mock(DocumentPageFigureCandidateRepository.class),
                mock(DocumentPageOcrResultRepository.class),
                references,
                mock(MediaAssetRepository.class),
                null,
                null,
                null,
                null,
                null,
                new DocumentPageFigureMapper(),
                events
        );
    }

    @Test
    void approvalRequiresUsableCaptionAndSourceReference() {
        figure.setRawCaptionText(null);
        figure.setSourceReference(null);

        IllegalArgumentException missingCaption = assertThrows(
                IllegalArgumentException.class,
                () -> approve()
        );
        assertEquals(
                "A usable figure caption is required before approval",
                missingCaption.getMessage()
        );

        figure.setRawCaptionText("Фигура");
        IllegalArgumentException missingReference = assertThrows(
                IllegalArgumentException.class,
                () -> approve()
        );
        assertEquals(
                "A source reference is required before figure approval",
                missingReference.getMessage()
        );
    }

    @Test
    void approvalPublishesPageAndMediaInvalidationEvents() {
        figure.setRawCaptionText("Старинна престилка");
        figure.setSourceReference(entity(new SourceReference(), 41L));

        var details = approve();

        assertEquals(FigureReviewState.APPROVED, details.reviewState());
        verify(events).page(
                figure.getDocumentPage(),
                ManagementEvent.Action.STATUS_CHANGED
        );
        verify(events).media(
                figure.getMediaAsset(),
                ManagementEvent.Action.STATUS_CHANGED
        );
    }

    @Test
    void changingPublishedMetadataReturnsFigureToPendingReview() {
        figure.setRawCaptionText("Старинна престилка");
        figure.setCorrectedCaptionText("Одобрено описание");
        figure.setSourceReference(entity(new SourceReference(), 41L));
        figure.setReviewState(FigureReviewState.APPROVED);
        figure.setReviewedBy("reviewer");
        figure.setReviewReason("Verified");

        SourceReference replacement = entity(new SourceReference(), 42L);
        when(references.findById(42L)).thenReturn(Optional.of(replacement));

        var details = service.update(
                21L,
                31L,
                RowVersionUtils.token(VERSION),
                new FigureCaptionUpdateCommand(
                        "Ново описание",
                        "Фиг. 7",
                        42L
                )
        );

        assertEquals(FigureReviewState.PENDING, details.reviewState());
        assertEquals("Ново описание", details.correctedCaptionText());
        verify(events).media(
                figure.getMediaAsset(),
                ManagementEvent.Action.UPDATED
        );
    }

    @Test
    void pendingFiguresRemainAvailableToTheReviewQuery() {
        when(pages.findById(21L)).thenReturn(Optional.of(figure.getDocumentPage()));
        when(figures.findByDocumentPage_IdOrderByFigureOrdinalAscIdAsc(21L))
                .thenReturn(List.of(figure));

        var result = service.findAll(21L);

        assertEquals(1, result.size());
        assertEquals(FigureReviewState.PENDING, result.getFirst().reviewState());
    }

    private fmi.ethnowear.application.dto.document.query.figure.DocumentPageFigureDetails approve() {
        return service.review(
                21L,
                31L,
                RowVersionUtils.token(VERSION),
                new FigureReviewCommand("Reviewed"),
                FigureReviewState.APPROVED,
                "reviewer"
        );
    }

    private DocumentPageFigure figure() {
        Document document = entity(new Document(), 11L);
        DocumentPage page = entity(new DocumentPage(), 21L);
        page.setDocument(document);
        page.setPageSequence(3);

        DocumentPageMedia pageMedia = entity(new DocumentPageMedia(), 22L);
        MediaAsset media = entity(new MediaAsset(), 23L);
        DocumentPageFigureCandidate candidate = entity(
                new DocumentPageFigureCandidate(),
                24L
        );
        DocumentProcessingJob job = entity(new DocumentProcessingJob(), 25L);

        DocumentPageFigure result = entity(new DocumentPageFigure(), 31L);
        result.setDocumentPage(page);
        result.setDocumentPageMedia(pageMedia);
        result.setMediaAsset(media);
        result.setFigureCandidate(candidate);
        result.setFigureOrdinal(1);
        result.setReviewState(FigureReviewState.PENDING);
        result.setProcessingJob(job);
        result.setProducingAttempt(1);
        ReflectionTestUtils.setField(result, "rowVersion", VERSION);
        return result;
    }

    private <T extends fmi.ethnowear.persistence.jpa.entity.AppendOnlyEntity> T entity(
            T entity,
            Long id
    ) {
        EntityTestUtils.setId(entity, id);
        return entity;
    }
}
