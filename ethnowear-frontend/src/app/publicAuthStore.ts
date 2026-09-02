import { useSyncExternalStore } from 'react'
import { PublicAuthClient, PublicAuthError, type PublicAuthConfig, type PublicProfile } from '../api/PublicAuthApi'
import { queryClient } from './queryClient'

type PublicAuthState = { config: PublicAuthConfig | null; profile: PublicProfile | null; ready: boolean; error: unknown }
const listeners = new Set<() => void>()
let state: PublicAuthState = { config: null, profile: null, ready: false, error: null }
let initialization: Promise<void> | null = null
function update(value: Partial<PublicAuthState>) {
    state = { ...state, ...value }
    listeners.forEach(listener => listener())
}
function clearIdentity() {
    update({ profile: null })
    queryClient.removeQueries({ queryKey: ['public', 'profile'] })
    queryClient.removeQueries({ queryKey: ['public', 'chat'] })
    if (typeof window !== 'undefined') window.dispatchEvent(new Event('ethnowear-public-owner-changed'))
}
export const publicAuthClient = new PublicAuthClient(clearIdentity)
export function initializePublicAuth() {
    if (!initialization) initialization = (async () => {
        try {
            const config = await publicAuthClient.config()
            update({ config })
            await publicAuthClient.refreshCsrf()
            try { update({ profile: await publicAuthClient.me() }) }
            catch (error) { if (!(error instanceof PublicAuthError && error.status === 401)) throw error }
        } catch (error) { update({ error }) }
        finally { update({ ready: true }) }
    })()
    return initialization
}
export async function loginPublicUser(credential: string) {
    const profile = await publicAuthClient.login(credential)
    clearIdentity()
    update({ profile, error: null })
}
export async function logoutPublicUser() {
    try { await publicAuthClient.logout() }
    catch (error) { if (!(error instanceof PublicAuthError && error.status === 401)) throw error }
    clearIdentity()
}
export function usePublicAuth() {
    return useSyncExternalStore(listener => { listeners.add(listener); return () => { listeners.delete(listener) } }, () => state)
}
