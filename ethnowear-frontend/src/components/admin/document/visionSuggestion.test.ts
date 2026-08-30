import { describe, expect, it } from 'vitest'
import type { TextSuggestionIssue } from '../../../types/document'
import { applyIssueCorrection, buildIssueComparison, overlapsApplied } from './visionSuggestion'

function issue(overrides: Partial<TextSuggestionIssue> = {}): TextSuggestionIssue {
    return { issueType: 'OCR_WORD', explanationBg: 'Възможна OCR грешка', originalText: 'грешка', originalContext: null, suggestedText: 'поправка', suggestedContext: null, startOffset: 7, endOffset: 13, safelyApplicable: true, confidence: .9, ...overrides }
}

describe('vision suggestion placement', () => {
    const text = 'Това е грешка в текста.'

    it('does not determine placement when offsets are missing', () => {
        expect(buildIssueComparison(issue({ startOffset: null, endOffset: null }), text)).toBeNull()
    })

    it('prevents overlapping corrections', () => {
        const comparison = buildIssueComparison(issue(), text)!
        const applied = [{ startOffset: 5, endOffset: 10, replacementLength: 4 }]

        expect(overlapsApplied(comparison, applied)).toBe(true)
        expect(applyIssueCorrection(text, issue(), comparison, applied)).toBeNull()
    })
})
