package fmi.ethnowear.application.exception;

import fmi.ethnowear.domain.model.document.processing.JobStatus;

public class VisionJobAlreadyActiveException extends RuntimeException {

    private final Long activeJobId;
    private final JobStatus activeJobStatus;
    private final Long documentPageId;
    private final Long ocrResultId;

    public VisionJobAlreadyActiveException(
            Long activeJobId,
            JobStatus activeJobStatus,
            Long documentPageId,
            Long ocrResultId
    ) {
        super("A vision review is already active for the current OCR result");
        this.activeJobId = activeJobId;
        this.activeJobStatus = activeJobStatus;
        this.documentPageId = documentPageId;
        this.ocrResultId = ocrResultId;
    }

    public Long getActiveJobId() {
        return activeJobId;
    }

    public JobStatus getActiveJobStatus() {
        return activeJobStatus;
    }

    public Long getDocumentPageId() {
        return documentPageId;
    }

    public Long getOcrResultId() {
        return ocrResultId;
    }
}
