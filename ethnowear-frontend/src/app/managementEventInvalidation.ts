import type { QueryClient } from '@tanstack/react-query'
import type { ManagementEvent, ManagementResyncRequired } from '../api/ManagementEventsApi'
import { documentQueryKeys, processingQueryKeys } from '../api/DocumentAdminApi'

export function createManagementEventConsumer(queryClient: QueryClient, lastEventId: { current: number | null }) {
    return {
        async handle(event: ManagementEvent) {
            if (lastEventId.current !== null && event.eventId <= lastEventId.current) return
            lastEventId.current = event.eventId
            await invalidateManagementEvent(queryClient, event)
        },
        async resync(event: ManagementResyncRequired) {
            lastEventId.current = Math.max(lastEventId.current ?? 0, event.latestEventId)
            await queryClient.invalidateQueries({ queryKey: documentQueryKeys.all, refetchType: 'none' })
            await queryClient.invalidateQueries({ queryKey: processingQueryKeys.all, refetchType: 'none' })
            await queryClient.refetchQueries({ queryKey: documentQueryKeys.all, type: 'active' })
            await queryClient.refetchQueries({ queryKey: processingQueryKeys.all, type: 'active' })
        },
    }
}

export async function invalidateManagementEvent(queryClient: QueryClient, event: ManagementEvent) {
    const documentId = event.documentId ?? (event.resourceType === 'DOCUMENT' ? event.resourceId : null)
    const pageId = event.pageId ?? (event.resourceType === 'DOCUMENT_PAGE' ? event.resourceId : null)
    const invalidations: Promise<unknown>[] = []
    const invalidate = (queryKey: readonly unknown[], exact = false) => invalidations.push(queryClient.invalidateQueries({ queryKey, exact }))

    if (event.resourceType === 'DOCUMENT') {
        invalidate(documentQueryKeys.lists())
        if (documentId !== null) {
            invalidate(documentQueryKeys.detail(documentId), true)
            invalidate(documentQueryKeys.progress(documentId), true)
        }
    } else if (event.resourceType === 'DOCUMENT_PAGE') {
        if (documentId !== null) {
            invalidate(documentQueryKeys.pagesRoot(documentId))
            invalidate(documentQueryKeys.figuresRoot(documentId))
            invalidate(documentQueryKeys.progress(documentId), true)
            if (pageId !== null) invalidate(documentQueryKeys.page(documentId, pageId))
            invalidate(documentQueryKeys.indexing(documentId), true)
            invalidate(documentQueryKeys.chunkEligibility(documentId), true)
            invalidate(documentQueryKeys.generatedChunksRoot(documentId))
        } else invalidate(documentQueryKeys.all)
    } else if (event.resourceType === 'PROCESSING_JOB') {
        invalidate(processingQueryKeys.all)
        if (documentId !== null) {
            invalidate(documentQueryKeys.jobsRoot(documentId))
            invalidate(documentQueryKeys.progress(documentId), true)
            invalidate(documentQueryKeys.detail(documentId), true)
            invalidate(documentQueryKeys.chunkJobsRoot(documentId))
            if (pageId !== null) invalidate(documentQueryKeys.page(documentId, pageId))
        } else invalidate(documentQueryKeys.all)
    } else if (event.resourceType === 'MEDIA_ASSET') {
        invalidate(['admin', 'media'])
        if (documentId !== null) invalidate(documentQueryKeys.detail(documentId), true)
        if (documentId !== null && pageId !== null) invalidate(documentQueryKeys.page(documentId, pageId))
    } else if (event.resourceType === 'INDEXING') {
        if (documentId !== null) {
            invalidate(documentQueryKeys.indexing(documentId), true)
            invalidate(documentQueryKeys.progress(documentId), true)
            invalidate(documentQueryKeys.generatedChunksRoot(documentId))
        } else invalidate(documentQueryKeys.all)
    }

    await Promise.all(invalidations)
}
