package fmi.ethnowear.application.dto.archive.media;

import fmi.ethnowear.domain.model.document.figure.FigureReviewState;

public record DocumentFigureMediaLinkDetails(
        Long documentId,
        Long documentPageId,
        Integer pageSequence,
        Long figureId,
        String caption,
        String printedFigureNumber,
        Long sourceReferenceId,
        FigureReviewState reviewState
) {
}
