package fmi.ethnowear.application.service.document.figure;

import fmi.ethnowear.application.exception.ActiveDocumentJobExistsException;
import fmi.ethnowear.application.service.document.processing.DocumentProcessingJobScheduler;
import fmi.ethnowear.domain.model.document.figure.FigureExtractionState;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageOcrResult;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageFigureCandidateRepository;
import fmi.ethnowear.persistence.jpa.repository.document.DocumentPageOcrResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FigureExtractionSchedulingExecutor {

    private final DocumentPageOcrResultRepository ocrResultRepository;
    private final DocumentPageFigureCandidateRepository candidateRepository;
    private final DocumentProcessingJobScheduler jobScheduler;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void schedule(Long ocrResultId) {
        DocumentPageOcrResult result = ocrResultRepository.findById(ocrResultId)
                .orElseThrow(() -> new IllegalArgumentException("OCR result does not exist"));

        if (!result.isCurrent()) {
            result.setFigureExtractionState(FigureExtractionState.OUTDATED);
            result.setFigureExtractionMessage(null);
            return;
        }

        if (candidateRepository.countByDocumentPageOcrResult_Id(ocrResultId) == 0) {
            result.setFigureExtractionState(FigureExtractionState.NOT_REQUESTED);
            result.setFigureExtractionMessage(null);
            return;
        }

        if (result.getDocumentPage() == null
                || result.getDocumentPageMedia() == null
                || result.getDocumentPageMedia().getMediaAsset() == null)
            throw new IllegalStateException("OCR result has no exact page rendition");

        try {
            jobScheduler.queueFigureExtraction(
                    result.getDocumentPage(),
                    result.getDocumentPageMedia().getMediaAsset(),
                    result
            );
            result.setFigureExtractionState(FigureExtractionState.PENDING);
            result.setFigureExtractionMessage(null);
        } catch (ActiveDocumentJobExistsException ex) {
            result.setFigureExtractionState(FigureExtractionState.PENDING);
            result.setFigureExtractionMessage(null);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordSchedulingFailure(Long ocrResultId) {
        ocrResultRepository.findById(ocrResultId).ifPresent(result -> {
            if (!result.isCurrent()) {
                result.setFigureExtractionState(FigureExtractionState.OUTDATED);
                result.setFigureExtractionMessage(null);
                return;
            }

            result.setFigureExtractionState(FigureExtractionState.SCHEDULING_FAILED);
            result.setFigureExtractionMessage(
                    "Figure extraction could not be scheduled and may be requested manually."
            );
        });
    }
}
