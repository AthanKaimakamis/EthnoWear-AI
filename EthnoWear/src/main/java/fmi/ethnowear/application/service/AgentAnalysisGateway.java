package fmi.ethnowear.application.service;

import fmi.ethnowear.agents.protocol.AnalyzeFeaturesPayload;
import fmi.ethnowear.agents.protocol.InterpretationResultPayload;

public interface AgentAnalysisGateway {
    InterpretationResultPayload analyze(AnalyzeFeaturesPayload payload);
}
