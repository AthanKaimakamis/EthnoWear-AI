export type PageResponse<T> = {
    content: T[]
    number: number
    size: number
    totalElements: number
    totalPages: number
    first: boolean
    last: boolean
    numberOfElements: number
    empty: boolean
}

export type PageRequest = {
    page?: number
    size?: number
    sort?: string
}

export type IdentifiableDto = {
    id: number
}
