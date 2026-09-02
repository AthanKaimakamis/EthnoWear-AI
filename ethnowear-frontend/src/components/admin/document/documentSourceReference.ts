import type { SourceReferenceDetails, SourceReferenceWriteDto } from '../../../types/archive'

export const emptySourceReference = (sourceId: number): SourceReferenceWriteDto => ({
    sourceId,
    chapter: null,
    pageFrom: null,
    pageTo: null,
    figureNumber: null,
    sectionTitle: null,
    catalogNumber: null,
    referenceUrl: null,
    accessedDate: null,
    locator: null,
    note: null,
})

export function findGeneralSourceReference(references: SourceReferenceDetails[], sourceId: number) {
    return references.find(reference => reference.sourceId === sourceId
        && reference.chapter === null
        && reference.pageFrom === null
        && reference.pageTo === null
        && reference.figureNumber === null
        && reference.sectionTitle === null
        && reference.catalogNumber === null
        && reference.referenceUrl === null
        && reference.accessedDate === null
        && reference.locator === null)
}
