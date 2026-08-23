import { clearAdminSession, getAdminAuthorization } from '../app/adminAuthStore'
import { localizedErrorMessages, localizedNonApiError, type ApiErrorDetails } from './errorLocalization'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

type QueryParams = Record<string, string | number | boolean | undefined | null>

type RequestOptions = {
    method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
    query?: QueryParams
    body?: unknown
    signal?: AbortSignal
    authorization?: 'auto' | 'protected' | 'none'
}

export function adminAuthorizationHeaders(headers?: HeadersInit) {
    const result = new Headers(headers)
    const authorization = getAdminAuthorization()
    if (authorization) result.set('Authorization', authorization)
    return result
}

export function handleAdminResponseStatus(status: number) {
    if (status === 401) clearAdminSession('unauthorized')
}

export class ApiError extends Error {
    status: number
    details: unknown

    constructor(message: string, status: number, details: unknown) {
        super(message);
        this.name = 'ApiError';
        this.status = status;
        this.details = details;
    }
}

export function apiErrorMessage(error: unknown, fallback = 'Unexpected error') {
    if (error instanceof ApiError) return localizedErrorMessages(error.status, error.details, error.message, fallback)[0]
    return localizedNonApiError(error, fallback)
}

export function apiErrorMessages(error: unknown, fallback = 'Unexpected error') {
    if (!(error instanceof ApiError)) return [apiErrorMessage(error, fallback)]

    return localizedErrorMessages(error.status, error.details, error.message, fallback)
}

function responseErrorMessage(data: unknown, status: number) {
    if (data && typeof data === 'object' && 'message' in data) {
        const message = (data as ApiErrorDetails).message
        if (typeof message === 'string' && message.trim()) return message.trim()
    }
    return `Request failed with status ${status}`
}

export function apiUrl(path: string, query?: QueryParams) {
    if (API_BASE_URL) {
        const url = new URL(path, API_BASE_URL)

        if(query) {
            Object.entries(query).forEach(([key, value]) => {
                if(value !== undefined && value !== null) {
                    url.searchParams.set(key, String(value));
                }
            })
        }

        return url.toString()
    }

    const params = new URLSearchParams()

    if (query) {
        Object.entries(query).forEach(([key, value]) => {
            if(value !== undefined && value !== null) {
                params.set(key, String(value))
            }
        })
    }

    const queryString = params.toString()
    return queryString ? `${path}?${queryString}` : path
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const requiresAdmin = options.authorization === 'protected'
        || (options.authorization !== 'none' && path.startsWith('/api/admin/'))
    const headers = requiresAdmin ? adminAuthorizationHeaders() : new Headers()
    headers.set('Accept', 'application/json')
    if (options.body) headers.set('Content-Type', 'application/json')
    const response = await fetch(apiUrl(path, options.query), {
        method: options.method ?? 'GET',
        headers,
        body: options.body ? JSON.stringify(options.body) : undefined,
        signal: options.signal,
    })

    const contentType = response.headers.get('content-type')
    const hasJson = contentType?.includes('application/json')

    const data = hasJson ? await response.json() : await response.text()

    if (!response.ok) {
        if (requiresAdmin) handleAdminResponseStatus(response.status)
        throw new ApiError(
            responseErrorMessage(data, response.status),
            response.status,
            data
        )
    }

    return data as T
}
