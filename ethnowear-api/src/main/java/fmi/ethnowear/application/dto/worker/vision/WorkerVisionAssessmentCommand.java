package fmi.ethnowear.application.dto.worker.vision;

import fmi.ethnowear.application.dto.worker.quality.WorkerQualityAssessmentCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record WorkerVisionAssessmentCommand(
        @NotNull @Valid WorkerQualityAssessmentCommand assessment,
        boolean requiresReview,
        @NotBlank String suggestedText,
        @NotBlank String modelName,
        @NotBlank String modelVersion,
        @NotBlank String promptVersion,
        @NotNull List<@Valid WorkerVisionIssueCommand> issues,
        @NotNull List<@Valid WorkerVisionUncertainPassageCommand> uncertainPassages
) {

    public WorkerVisionAssessmentCommand {
        if (issues != null)
            issues = List.copyOf(issues);

        if (uncertainPassages != null)
            uncertainPassages = List.copyOf(uncertainPassages);
    }
}
