package fmi.ethnowear.application.port.analysis;

import fmi.ethnowear.application.model.analysis.AnalyzeFeaturesPayload;
import fmi.ethnowear.application.model.analysis.InterpretationResultPayload;

public interface AnalysisGateway {
    InterpretationResultPayload analyze(AnalyzeFeaturesPayload payload);
}
