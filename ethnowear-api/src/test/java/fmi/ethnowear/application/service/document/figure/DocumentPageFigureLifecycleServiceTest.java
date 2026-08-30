package fmi.ethnowear.application.service.document.figure;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentProcessingJob;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import fmi.ethnowear.testutil.EntityTestUtils;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static fmi.ethnowear.support.RepositoryTestProxies.proxy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class DocumentPageFigureLifecycleServiceTest {

    @Test
    void marksOnlyPreviousResultsOutdatedAndClearsReviewMetadata() {
        DocumentProcessingJob currentJob = new DocumentProcessingJob();
        EntityTestUtils.setId(currentJob, 11L);
        DocumentProcessingJob previousJob = new DocumentProcessingJob();
        EntityTestUtils.setId(previousJob, 10L);

        DocumentPageFigure current = figure(currentJob, FigureReviewState.PENDING);
        DocumentPageFigure previous = figure(previousJob, FigureReviewState.APPROVED);
        previous.setReviewedBy("reviewer");
        previous.setReviewedAt(LocalDateTime.now());
        previous.setReviewReason("Verified");

        DocumentPageFigureRepository repository = proxy(
                DocumentPageFigureRepository.class,
                (ignored, method, arguments) -> {
                    if (method.getName().equals("findByDocumentPage_IdAndReviewStateIn"))
                        return List.of(current, previous);
                    throw new AssertionError("Unexpected repository call: " + method.getName());
                }
        );

        new DocumentPageFigureLifecycleService(
                repository,
                mock(ManagementEventPublisher.class)
        )
                .markPreviousResultsOutdated(7L, 11L);

        assertEquals(FigureReviewState.PENDING, current.getReviewState());
        assertEquals(FigureReviewState.OUTDATED, previous.getReviewState());
        assertNull(previous.getReviewedBy());
        assertNull(previous.getReviewedAt());
        assertNull(previous.getReviewReason());
    }

    private DocumentPageFigure figure(
            DocumentProcessingJob job,
            FigureReviewState state
    ) {
        DocumentPageFigure figure = new DocumentPageFigure();
        figure.setProcessingJob(job);
        figure.setReviewState(state);
        return figure;
    }
}
