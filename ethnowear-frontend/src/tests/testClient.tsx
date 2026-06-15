import { useState } from 'react'
import { analyzeFeatures } from "../api/analyzeApi";
import { getFullReference } from "../api/ReferenceApi";
import type { ReferenceData } from "../types/reference";
import type { AnalyzeResponse } from "../types/analyze";

export function TestClient() {
    const [reference, setReference] = useState<ReferenceData | null>(null);
    const [analysis, setAnalysis] = useState<AnalyzeResponse | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [loading, setLoading] = useState(false);

    async function loadReference(){
        setLoading(true);
        setError(null);

        try{
            const data = await getFullReference('bg')
            setReference(data)
        } catch(err){
            setError(err instanceof Error ? err.message : 'Unknown error');
        } finally {
            setLoading(false);
        }
    }

    async function runAnalysis(){
        setLoading(true);
        setError(null);

        try {
            const data = await analyzeFeatures({
                selectedOrnaments: ['RhombusOrnament'],
                selectedColors: ['RedColor'],
                selectedTechniques: [],
                selectedMotif: null,
                selectedRegion: null,
                selectedRegionalEmbroidery: null,
                language: 'bg'
            })

            setAnalysis(data)
        } catch (err) {
            setError(err instanceof Error ? err.message : 'Unknown error');
        } finally {
            setLoading(false);
        }
    }

    return (
        <main style={{ padding: 24, fontFamily: 'system-ui, sans-serif' }}>
            <h1>EthnoWear API Client Test</h1>

            <div style={{ display: 'flex', gap: 12 }}>
                <button onClick={loadReference} disabled={loading}>
                    Load reference
                </button>

                <button onClick={runAnalysis} disabled={loading}>
                    Run analysis
                </button>
            </div>

            {loading && <p>Loading...</p>}

            {error && (
                <pre style={{ color: 'crimson', whiteSpace: 'pre-wrap' }}>
                    {error}
                </pre>
            )}

            {reference && (
                <section>
                    <h2>Reference</h2>
                    <p>Regions: {reference.regions.length}</p>
                    <p>Ornaments: {reference.ornaments.length}</p>
                    <p>Colors: {reference.colors.length}</p>
                    <p>Techniques: {reference.techniques.length}</p>
                </section>
            )}

            {analysis && (
                <section>
                    <h2>Analysis</h2>
                    <p>{analysis.explanation}</p>

                    <pre>{JSON.stringify(analysis, null, 2)}</pre>
                </section>
            )}
        </main>
    )
}