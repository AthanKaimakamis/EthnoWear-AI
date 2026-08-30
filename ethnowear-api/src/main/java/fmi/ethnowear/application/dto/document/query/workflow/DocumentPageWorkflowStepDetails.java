package fmi.ethnowear.application.dto.document.query.workflow;

import fmi.ethnowear.domain.model.document.processing.JobStatus;

public record DocumentPageWorkflowStepDetails(
        DocumentPageWorkflowStepType step,
        DocumentPageWorkflowStepStatus status,
        Long jobId,
        JobStatus jobStatus,
        String message
) {
}
