import { useEffect, useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getAdminMediaContent } from '../../../api/ArchiveAdminApi'

export function useAdminMediaContent(mediaAssetId: number | null | undefined) {
    const query = useQuery({
        queryKey: ['admin', 'media', 'content', mediaAssetId],
        queryFn: ({ signal }) => getAdminMediaContent(mediaAssetId!, signal),
        enabled: Boolean(mediaAssetId),
        staleTime: 60_000,
    })
    const url = useMemo(() => query.data ? URL.createObjectURL(query.data) : null, [query.data])

    useEffect(() => () => { if (url) URL.revokeObjectURL(url) }, [url])

    return { ...query, url }
}
