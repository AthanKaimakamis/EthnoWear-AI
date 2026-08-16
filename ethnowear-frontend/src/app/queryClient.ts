import { QueryClient } from '@tanstack/react-query'

export const publicQueryKeys = {
    all: ['public'] as const,
    catalogue: ['public', 'catalogue'] as const,
    archive: ['public', 'archive'] as const,
}

export const queryClient = new QueryClient({
    defaultOptions: {
        queries: {
            gcTime: 30 * 60 * 1000,
            refetchOnWindowFocus: false,
            retry: 1,
        },
    },
})

export function invalidatePublicQueries() {
    return queryClient.invalidateQueries({ queryKey: publicQueryKeys.all })
}
