const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

type RequestOptions = {
    method?: 'GET' | 'POST' | 'PUT' | 'DELETE'
    query?: Record<string, string | number | boolean | undefined | null>
    body?: unknown
    signal?: AbortSignal
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

function buildUrl(path: string, query?: RequestOptions['query']) {
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
    const response = await fetch(buildUrl(path, options.query), {
        method: options.method ?? 'GET',
        headers: {
            Accept: 'application/json',
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
