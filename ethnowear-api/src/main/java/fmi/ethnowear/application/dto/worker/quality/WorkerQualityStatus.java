package fmi.ethnowear.application.dto.worker.quality;

import fmi.ethnowear.domain.model.document.quality.QualityStatus;

public enum WorkerQualityStatus {

    PASS(QualityStatus.HIGH_QUALITY),
    WARNING(QualityStatus.MINOR_REVIEW),
    FAIL(QualityStatus.POOR_QUALITY),
    REVIEW_REQUIRED(QualityStatus.REVIEW_REQUIRED);

    private final QualityStatus qualityStatus;

    WorkerQualityStatus(QualityStatus qualityStatus) {
        this.qualityStatus = qualityStatus;
    }

    public QualityStatus toDomainStatus() {
        return qualityStatus;
    }
}
