export function parseServerDateTime(value: string) {
    const includesTimeZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(value)
    return new Date(includesTimeZone ? value : `${value}Z`)
}

export function isFutureServerDateTime(value: string | null | undefined, now = Date.now()) {
    if (!value) return false
    const timestamp = parseServerDateTime(value).getTime()
    return Number.isFinite(timestamp) && timestamp > now
}
