package fmi.ethnowear.application.dto.worker.job;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.util.Set;

@Schema(example = """
        {
          "workerId": "page-extractor-1",
          "supportedJobTypes": ["PAGE_EXTRACTION"],
          "leaseSeconds": 120
        }
        """)
public record WorkerJobClaimCommand(
        @NotBlank
        @Size(max = 150)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*")
        String workerId,

        @NotEmpty
        @Size(max = 8)
        Set<WorkerJobType> supportedJobTypes,

        @Positive
        Integer leaseSeconds
) {

    public WorkerJobClaimCommand {
        if(supportedJobTypes != null)
            supportedJobTypes = Set.copyOf(supportedJobTypes);
    }
}