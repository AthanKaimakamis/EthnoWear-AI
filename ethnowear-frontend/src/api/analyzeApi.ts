import { apiRequest } from "./http";
import type { AnalyzeRequest, AnalyzeResponse } from "../types/analyze";

export function analyzeFeatures(request: AnalyzeRequest) {
    return apiRequest<AnalyzeResponse>('/api/analyze', {
        method: 'POST',
        body: request,
    })
}