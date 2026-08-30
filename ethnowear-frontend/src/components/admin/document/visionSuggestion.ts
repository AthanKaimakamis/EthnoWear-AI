import type { TextSuggestionIssue } from '../../../types/document'

export type HighlightedExcerpt = {
    prefix: string
    changed: string
    suffix: string
}

export type IssueComparison = {
    before: HighlightedExcerpt
    after: HighlightedExcerpt
    startOffset: number
    endOffset: number
}

export type AppliedCorrection = {
    startOffset: number
    endOffset: number
    replacementLength: number
}

const CONTEXT_RADIUS = 90

export function buildIssueComparison(issue: TextSuggestionIssue, ocrText: string): IssueComparison | null {
    const start = issue.startOffset
    const end = issue.endOffset
    if (!Number.isInteger(start) || !Number.isInteger(end) || start === null || start === undefined || end === null || end === undefined || start < 0 || end <= start || end > ocrText.length) return null

    const original = issue.originalText
    if (!original || ocrText.slice(start, end) !== original) return null

    const contextStart = contextBoundary(ocrText, start, -1)
    const contextEnd = contextBoundary(ocrText, end, 1)
    const fallbackPrefix = ocrText.slice(contextStart, start)
    const fallbackSuffix = ocrText.slice(end, contextEnd)
    const before = highlightedContext(issue.originalContext, original)
        ?? { prefix: fallbackPrefix, changed: original, suffix: fallbackSuffix }
    const replacement = issue.suggestedText ?? ''
    const after = highlightedContext(issue.suggestedContext, replacement)
        ?? { prefix: before.prefix, changed: replacement, suffix: before.suffix }

    return {
        before,
        after,
        startOffset: start,
        endOffset: end,
    }
}

export function buildIssueDisplayComparison(issue: TextSuggestionIssue, ocrText: string): IssueComparison | null {
    const applicable = buildIssueComparison(issue, ocrText)
    if (applicable) return applicable
    if (!issue.originalText) return null

    const before = highlightedContext(issue.originalContext, issue.originalText)
        ?? { prefix: '', changed: issue.originalText, suffix: '' }
    const replacement = issue.suggestedText ?? ''
    const after = highlightedContext(issue.suggestedContext, replacement)
        ?? { prefix: before.prefix, changed: replacement, suffix: before.suffix }

    return { before, after, startOffset: -1, endOffset: -1 }
}

function highlightedContext(context: string | null, changed: string): HighlightedExcerpt | null {
    if (!context || !changed) return null
    const start = context.indexOf(changed)
    if (start < 0) return null
    return {
        prefix: context.slice(0, start),
        changed,
        suffix: context.slice(start + changed.length),
    }
}

export function applyIssueCorrection(
    editableText: string,
    issue: TextSuggestionIssue,
    comparison: IssueComparison,
    applied: AppliedCorrection[],
): string | null {
    if (!issue.safelyApplicable || issue.suggestedText === null || overlapsApplied(comparison, applied)) return null

    const adjustedStart = comparison.startOffset + applied
        .filter(item => item.endOffset <= comparison.startOffset)
        .reduce((total, item) => total + item.replacementLength - (item.endOffset - item.startOffset), 0)
    const adjustedEnd = adjustedStart + (comparison.endOffset - comparison.startOffset)

    if (editableText.slice(adjustedStart, adjustedEnd) !== issue.originalText) return null
    return editableText.slice(0, adjustedStart) + issue.suggestedText + editableText.slice(adjustedEnd)
}

export function overlapsApplied(comparison: IssueComparison, applied: AppliedCorrection[]) {
    return applied.some(item => comparison.startOffset < item.endOffset && comparison.endOffset > item.startOffset)
}

function contextBoundary(text: string, offset: number, direction: -1 | 1) {
    const limit = direction < 0 ? Math.max(0, offset - CONTEXT_RADIUS) : Math.min(text.length, offset + CONTEXT_RADIUS)
    const segment = direction < 0 ? text.slice(limit, offset) : text.slice(offset, limit)
    const newline = direction < 0 ? segment.lastIndexOf('\n') : segment.indexOf('\n')
    if (newline >= 0) return direction < 0 ? limit + newline + 1 : offset + newline
    return limit
}
