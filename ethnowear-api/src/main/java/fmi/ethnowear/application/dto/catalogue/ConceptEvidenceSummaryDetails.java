package fmi.ethnowear.application.dto.catalogue;

public record ConceptEvidenceSummaryDetails(
        long evidenceCount,
        Long representativeMediaAssetId
) {
    public static ConceptEvidenceSummaryDetails empty() {
        return new ConceptEvidenceSummaryDetails(0, null);
    }
}
