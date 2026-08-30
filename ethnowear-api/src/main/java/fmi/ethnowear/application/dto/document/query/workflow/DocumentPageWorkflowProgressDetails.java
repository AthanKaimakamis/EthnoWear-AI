package fmi.ethnowear.application.dto.document.query.workflow;

import java.util.List;

public record DocumentPageWorkflowProgressDetails(
        Long documentId,
        Long pageId,
        int completedSteps,
        int totalSteps,
        List<DocumentPageWorkflowStepDetails> steps
) {
    public DocumentPageWorkflowProgressDetails {
        steps = List.copyOf(steps);
    }
}
