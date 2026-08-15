package fmi.ethnowear.infrastructure.agent.jade.protocol;

import fmi.ethnowear.application.model.analysis.AnalyzeFeaturesPayload;
import fmi.ethnowear.application.model.analysis.InterpretationResultPayload;

import java.util.concurrent.CompletableFuture;

public record AnalysisCommand(
        AnalyzeFeaturesPayload payload,
        CompletableFuture<InterpretationResultPayload> result
) {
}
