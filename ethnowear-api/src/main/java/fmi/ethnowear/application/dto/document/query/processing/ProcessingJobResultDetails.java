package fmi.ethnowear.application.dto.document.query.processing;

import fmi.ethnowear.application.dto.document.query.quality.DocumentPageQualitySummaryDetails;

import java.util.List;

public record ProcessingJobResultDetails(
        List<Long> producedMediaAssetIds,
        Long ocrResultId,
        DocumentPageQualitySummaryDetails qualityAssessment
) {
    public ProcessingJobResultDetails {
        producedMediaAssetIds = List.copyOf(producedMediaAssetIds);
    }
}
