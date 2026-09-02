import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import AdminMediaThumbnail from './AdminMediaThumbnail'
import { clearAdminSession, setAdminSession } from '../../../app/adminAuthStore'

afterEach(() => {
    clearAdminSession()
    vi.unstubAllGlobals()
})

describe('AdminMediaThumbnail', () => {
    it('loads management media as an authenticated blob', async () => {
        setAdminSession({ accessToken: 'signed-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'admin' })
        const fetchMock = vi.fn().mockResolvedValue(new Response('image', { headers: { 'Content-Type': 'image/jpeg' } }))
        vi.stubGlobal('fetch', fetchMock)
        vi.stubGlobal('URL', { ...URL, createObjectURL: vi.fn(() => 'blob:admin-media'), revokeObjectURL: vi.fn() })
        const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })

        render(<QueryClientProvider client={client}><AdminMediaThumbnail mediaAssetId={17} alt="Private image" /></QueryClientProvider>)

        await waitFor(() => expect(screen.getByAltText('Private image')).toHaveAttribute('src', 'blob:admin-media'))
        expect(fetchMock).toHaveBeenCalledWith('/api/admin/media-assets/17/content', expect.objectContaining({
            headers: expect.anything(),
        }))
        expect(new Headers(fetchMock.mock.calls[0][1].headers).get('Authorization')).toBe('Bearer signed-token')
    })
})
