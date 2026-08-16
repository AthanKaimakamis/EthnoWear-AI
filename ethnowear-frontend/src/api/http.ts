import { getAdminAuthorization } from '../app/adminAuthStore'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

type QueryParams = Record<string, string | number | boolean | undefined | null>

type RequestOptions = {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
    query?: QueryParams
    body?: unknown
    signal?: AbortSignal
}

type ApiErrorBody = {
    message?: unknown
    fields?: unknown
    errors?: unknown
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
    if (error instanceof ApiError && error.details && typeof error.details === 'object' && 'message' in error.details) {
        return String(error.details.message)
    }
    return error instanceof Error ? error.message : fallback
}

export function apiErrorMessages(error: unknown, fallback = 'Unexpected error') {
    if (!(error instanceof ApiError)) return [apiErrorMessage(error, fallback)]

    const details = error.details as ApiErrorBody | null
    const messages: string[] = []
    if (details?.message) messages.push(String(details.message))

    if (details?.fields && typeof details.fields === 'object') {
        Object.entries(details.fields as Record<string, unknown>).forEach(([field, message]) => {
            messages.push(`${field}: ${String(message)}`)
        })
    }

    if (Array.isArray(details?.errors)) {
        details.errors.forEach(item => {
            if (typeof item === 'string') messages.push(item)
            else if (item && typeof item === 'object' && 'message' in item) {
                messages.push(String(item.message))
            }
        })
    }

    if (messages.length > 0) return [...new Set(messages)]
    return [apiErrorMessage(error, fallback)]
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
    const adminAuthorization = path.startsWith('/api/admin/') ? getAdminAuthorization() : null
    const response = await fetch(apiUrl(path, options.query), {
        method: options.method ?? 'GET',
        headers: {
            Accept: 'application/json',
            ...(adminAuthorization ? { Authorization: adminAuthorization } : {}),
            ...(options.body ? { 'Content-Type': 'application/json' } : {}),
        },
        body: options.body ? JSON.stringify(options.body) : undefined,
        signal: options.signal,
    })

    const contentType = response.headers.get('content-type')
    const hasJson = contentType?.includes('application/json')

    const data = hasJson ? await response.json() : await response.text()

    if (!response.ok) {
        throw new ApiError(
            `Request failed with status ${response.status}`,
            response.status,
            data
        )
    }

    return data as T
}
