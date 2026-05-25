package fmi.ethnowear.api.dto.analysis;

import jakarta.validation.constraints.Size;

import java.util.List;

public record AnalyzeRequest(
        @Size(max = 20) List<String> ornaments,
        @Size(max = 20) List<String> colors,
        @Size(max = 20) List<String> techniques,
        String motif,
        String region,
        String regionalEmbroidery,
        String language
) {
}
