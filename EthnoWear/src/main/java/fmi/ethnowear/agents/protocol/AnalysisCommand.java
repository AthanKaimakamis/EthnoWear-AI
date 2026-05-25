package fmi.ethnowear.agents.protocol;

import java.util.concurrent.CompletableFuture;

public record AnalysisCommand(
        AnalyzeFeaturesPayload payload,
        CompletableFuture<InterpretationResultPayload> result
) {
}
