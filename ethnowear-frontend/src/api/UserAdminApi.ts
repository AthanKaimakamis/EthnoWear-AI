import type { RoleName } from '../app/permissions'
import type {
    CreatedUser, PageResponse, TemporaryPassword, UserCreateCommand, UserDetails,
    UserProfile, UserSummary,
} from '../types/userAdmin'
import { apiRequest } from './http'

export function getUsers(params: { search?: string; page: number; size: number; sort?: string }, signal?: AbortSignal) {
    return apiRequest<PageResponse<UserSummary>>('/api/admin/users', { query: params, signal })
}

export function getUser(userId: number, signal?: AbortSignal) {
    return apiRequest<UserDetails>(`/api/admin/users/${userId}`, { signal })
}

export function createUser(command: UserCreateCommand) {
    return apiRequest<CreatedUser>('/api/admin/users', { method: 'POST', body: command })
}

export function updateUserProfile(userId: number, profile: UserProfile) {
    return apiRequest<UserDetails>(`/api/admin/users/${userId}/profile`, { method: 'PUT', body: profile })
}

export function assignUserRole(userId: number, role: RoleName) {
    return apiRequest<UserDetails>(`/api/admin/users/${userId}/roles/${role}`, { method: 'PUT' })
}

export function removeUserRole(userId: number, role: RoleName) {
    return apiRequest<UserDetails>(`/api/admin/users/${userId}/roles/${role}`, { method: 'DELETE' })
}

export function enableUser(userId: number) {
    return apiRequest<UserDetails>(`/api/admin/users/${userId}/enable`, { method: 'POST' })
}

export function disableUser(userId: number) {
    return apiRequest<UserDetails>(`/api/admin/users/${userId}/disable`, { method: 'POST' })
}

export function unlockUser(userId: number) {
    return apiRequest<UserDetails>(`/api/admin/users/${userId}/unlock`, { method: 'POST' })
}

export function resetUserPassword(userId: number) {
    return apiRequest<TemporaryPassword>(`/api/admin/users/${userId}/reset-password`, { method: 'POST' })
}
