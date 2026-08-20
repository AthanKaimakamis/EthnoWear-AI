import type { RoleName } from '../app/permissions'

export type UserProfile = {
    firstName: string
    lastName: string
    email: string | null
    phone: string | null
    addressLine1: string | null
    addressLine2: string | null
    city: string | null
    postalCode: string | null
    countryCode: string | null
}

export type UserSummary = {
    id: number
    username: string
    firstName: string
    lastName: string
    email: string | null
    enabled: boolean
    passwordChangeRequired: boolean
    roles: RoleName[]
}

export type UserDetails = {
    id: number
    username: string
    profile: UserProfile
    roles: RoleName[]
    enabled: boolean
    passwordChangeRequired: boolean
    temporaryPasswordExpiresAt: string | null
    lockedUntil: string | null
    lastLoginAt: string | null
    createdAt: string
    updatedAt: string
}

export type UserCreateCommand = {
    username: string
    profile: UserProfile
    roles: RoleName[]
}

export type TemporaryPassword = {
    temporaryPassword: string
    expiresAt: string
}

export type CreatedUser = {
    user: UserDetails
    credentials: TemporaryPassword
}

export type PageResponse<T> = {
    content: T[]
    totalElements: number
    totalPages: number
    number: number
    size: number
}
