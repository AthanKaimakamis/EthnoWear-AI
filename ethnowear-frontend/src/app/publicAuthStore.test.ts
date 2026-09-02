import { afterEach, expect, it, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { queryClient } from './queryClient'
import { initializePublicAuth, logoutPublicUser, usePublicAuth } from './publicAuthStore'

afterEach(() => { vi.unstubAllGlobals(); queryClient.clear() })
it('restores cookie profile and clears only public identity caches on logout', async () => {
    const json = (value: unknown) => new Response(JSON.stringify(value), { status: 200 })
    const fetch = vi.fn().mockResolvedValueOnce(json({ enabled: true, googleClientId: 'client' }))
        .mockResolvedValueOnce(json({ headerName: 'X-PUBLIC-CSRF', token: 'fresh-token' }))
        .mockResolvedValueOnce(json({ userId: 'public-user', displayName: 'Public User', email: 'public@example.org' }))
        .mockResolvedValueOnce(new Response(null, { status: 204 }))
    vi.stubGlobal('fetch', fetch)
    const { result } = renderHook(() => usePublicAuth())
    await act(() => initializePublicAuth())
    expect(result.current.profile?.userId).toBe('public-user')
    queryClient.setQueryData(['public', 'chat', 'history'], ['private'])
    queryClient.setQueryData(['admin', 'users'], ['management'])
    queryClient.setQueryData(['public', 'catalogue'], ['archive'])
    await act(() => logoutPublicUser())
    expect(result.current.profile).toBeNull()
    expect(queryClient.getQueryData(['public', 'chat', 'history'])).toBeUndefined()
    expect(queryClient.getQueryData(['admin', 'users'])).toEqual(['management'])
    expect(queryClient.getQueryData(['public', 'catalogue'])).toEqual(['archive'])
})
