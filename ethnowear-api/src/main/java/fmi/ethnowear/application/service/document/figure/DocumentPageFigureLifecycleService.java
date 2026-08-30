package fmi.ethnowear.application.service.document.figure;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureRepository;
import fmi.ethnowear.application.model.event.ManagementEvent;
import fmi.ethnowear.application.service.event.ManagementEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DocumentPageFigureLifecycleService {

    private final DocumentPageFigureRepository repository;
    private final ManagementEventPublisher managementEvents;

    @Transactional(propagation = Propagation.MANDATORY)
    public void markPreviousResultsOutdated(Long pageId, Long currentJobId) {
        List<DocumentPageFigure> figures = repository
                .findByDocumentPage_IdAndReviewStateIn(
                        pageId,
                        List.of(
                                FigureReviewState.PENDING,
                                FigureReviewState.APPROVED,
                                FigureReviewState.REJECTED
                        )
                );

        List<DocumentPageFigure> outdated = figures.stream()
                .filter(figure -> figure.getProcessingJob() == null
                        || !Objects.equals(
                        figure.getProcessingJob().getId(),
                        currentJobId
                ))
                .toList();
        outdated.forEach(figure -> {
                    figure.setReviewState(FigureReviewState.OUTDATED);
                    figure.setReviewedBy(null);
                    figure.setReviewedAt(null);
                    figure.setReviewReason(null);
                    managementEvents.media(
                            figure.getMediaAsset(),
                            ManagementEvent.Action.STATUS_CHANGED
                    );
                });

        if (!outdated.isEmpty())
            managementEvents.page(
                    outdated.getFirst().getDocumentPage(),
                    ManagementEvent.Action.STATUS_CHANGED
            );
    }
}
