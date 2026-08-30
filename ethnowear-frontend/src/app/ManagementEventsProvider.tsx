import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Alert, Snackbar } from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import {
    connectManagementEvents,
    ManagementEventsHttpError,
} from '../api/ManagementEventsApi'
import { clearAdminSession, getAdminAuthorization, subscribeToAdminSessionChanges } from './adminAuthStore'
import { createManagementEventConsumer } from './managementEventInvalidation'

const INITIAL_RECONNECT_DELAY = 3_000
const MAX_RECONNECT_DELAY = 30_000

export function ManagementEventsProvider({ children }: { children: ReactNode }) {
    const { t } = useTranslation()
    const queryClient = useQueryClient()
    const lastEventId = useRef<number | null>(null)
    const [authorization, setAuthorization] = useState(getAdminAuthorization)
    const [forbidden, setForbidden] = useState(false)

    useEffect(() => subscribeToAdminSessionChanges(() => setAuthorization(getAdminAuthorization())), [])

    useEffect(() => {
        if (!authorization) return
        const controller = new AbortController()
        const consumer = createManagementEventConsumer(queryClient, lastEventId)

        async function listen() {
            let reconnectDelay = INITIAL_RECONNECT_DELAY
            while (!controller.signal.aborted) {
                try {
                    await connectManagementEvents({
                        authorization: authorization!,
                        lastEventId: lastEventId.current,
                        signal: controller.signal,
                        onConnected: () => { reconnectDelay = INITIAL_RECONNECT_DELAY; setForbidden(false) },
                        onManagementEvent: event => { void consumer.handle(event) },
                        onResyncRequired: event => { void consumer.resync(event) },
                    })
                } catch (error) {
                    if (controller.signal.aborted) return
                    if (error instanceof ManagementEventsHttpError && error.status === 401) {
                        clearAdminSession('unauthorized')
                        return
                    }
                    if (error instanceof ManagementEventsHttpError && error.status === 403) {
                        setForbidden(true)
                        return
                    }
                    await wait(reconnectDelay, controller.signal)
                    reconnectDelay = Math.min(reconnectDelay * 2, MAX_RECONNECT_DELAY)
                }
            }
        }

        void listen()
        return () => controller.abort()
    }, [authorization, queryClient])

    return <>
        {children}
        <Snackbar open={forbidden} anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}>
            <Alert severity="error" onClose={() => setForbidden(false)}>{t('managementEvents.forbidden')}</Alert>
        </Snackbar>
    </>
}

function wait(milliseconds: number, signal: AbortSignal) {
    return new Promise<void>(resolve => {
        const timeout = window.setTimeout(done, milliseconds)
        function done() {
            window.clearTimeout(timeout)
            signal.removeEventListener('abort', done)
            resolve()
        }
        signal.addEventListener('abort', done, { once: true })
    })
}
