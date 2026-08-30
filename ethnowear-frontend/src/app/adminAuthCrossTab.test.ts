import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

type ChannelMessage = Record<string, unknown>

class FakeBroadcastChannel {
    static instances: FakeBroadcastChannel[] = []
    readonly messages: ChannelMessage[] = []
    readonly name: string
    private listener: ((event: MessageEvent) => void) | null = null

    constructor(name: string) {
        this.name = name
        FakeBroadcastChannel.instances.push(this)
    }

    addEventListener(_type: string, listener: (event: MessageEvent) => void) {
        this.listener = listener
    }

    postMessage(message: ChannelMessage) {
        this.messages.push(message)
    }

    emit(message: ChannelMessage) {
        this.listener?.(new MessageEvent('message', { data: message }))
    }
}

describe('cross-tab admin session handoff', () => {
    beforeEach(() => {
        vi.resetModules()
        vi.stubGlobal('BroadcastChannel', FakeBroadcastChannel)
        FakeBroadcastChannel.instances = []
        sessionStorage.clear()
    })

    afterEach(() => vi.unstubAllGlobals())

    it('requests and accepts a valid Bearer session from another tab', async () => {
        const store = await import('./adminAuthStore')
        const restoring = store.restoreAdminSessionFromAnotherTab(1_000)
        const channel = FakeBroadcastChannel.instances[0]
        const request = channel.messages[0]

        channel.emit({
            type: 'session-response',
            source: 'existing-tab',
            target: request.source,
            requestId: request.requestId,
            session: { accessToken: 'shared-token', tokenType: 'Bearer', expiresAt: '2099-01-01T00:00:00Z', username: 'admin' },
        })

        await expect(restoring).resolves.toMatchObject({ accessToken: 'shared-token', username: 'admin' })
        expect(store.getAdminAuthorization()).toBe('Bearer shared-token')
        expect(sessionStorage.getItem('ethnowear.admin.session')).toContain('shared-token')
    })
})
