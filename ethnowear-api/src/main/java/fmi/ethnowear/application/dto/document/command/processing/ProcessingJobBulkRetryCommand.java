package fmi.ethnowear.application.dto.document.command.processing;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ProcessingJobBulkRetryCommand(
        @NotEmpty
        @Size(max = 100)
        List<@NotNull @Positive Long> jobIds
) {

    public ProcessingJobBulkRetryCommand {
        if (jobIds != null)
            jobIds = List.copyOf(jobIds);
    }
}
