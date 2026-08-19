package fmi.ethnowear.application.dto.document.query;

import java.time.LocalDateTime;
import java.util.List;

public record DocumentPageDetails(
        DocumentPageSummaryDetails summary,
        String rawOcrText,
        String correctedText,
        String ocrEngine,
        String ocrEngineVersion,
        String ocrLanguage,
        String reviewer,
        LocalDateTime reviewedAt,
        String reviewNotes,
        Long canonicalDocumentPageId,
        String provenanceNote,
        String provenanceReviewedBy,
        LocalDateTime provenanceReviewedAt,
        List<DocumentPageMediaDetails> media
) {
    public DocumentPageDetails {
        media = List.copyOf(media);
    }
}
