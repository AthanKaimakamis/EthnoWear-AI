package fmi.ethnowear.application.service.document.figure;

import fmi.ethnowear.application.dto.document.query.figure.DocumentPageFigureDetails;
import fmi.ethnowear.persistence.jpa.entity.document.DocumentPageFigure;
import fmi.ethnowear.util.RowVersionUtils;
import org.springframework.stereotype.Component;

@Component
public class DocumentPageFigureMapper {

    public DocumentPageFigureDetails toDetails(DocumentPageFigure figure) {
        return new DocumentPageFigureDetails(
                figure.getId(),
                figure.getDocumentPage().getId(),
                figure.getDocumentPageMedia().getId(),
                figure.getMediaAsset().getId(),
                figure.getSourceReference() == null
                        ? null
                        : figure.getSourceReference().getId(),
                figure.getFigureCandidate() == null
                        ? null
                        : figure.getFigureCandidate().getId(),
                figure.getFigureOrdinal(),
                figure.getPrintedFigureNumber(),
                figure.getNormalizedX(),
                figure.getNormalizedY(),
                figure.getNormalizedWidth(),
                figure.getNormalizedHeight(),
                figure.getRawCaptionText(),
                figure.getCorrectedCaptionText(),
                figure.getReviewState(),
                figure.getReviewedBy(),
                figure.getReviewedAt(),
                figure.getReviewReason(),
                figure.getDetectionConfidence(),
                figure.getProcessingJob().getId(),
                figure.getProducingAttempt(),
                figure.getCreatedAt(),
                figure.getUpdatedAt(),
                RowVersionUtils.token(figure.getRowVersion())
        );
    }
}
