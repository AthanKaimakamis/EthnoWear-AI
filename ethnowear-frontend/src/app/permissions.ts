export const roleNames = ['ADMINISTRATOR', 'REVIEWER', 'EDITOR'] as const

export type RoleName = typeof roleNames[number]

export function hasRole(roles: readonly RoleName[], role: RoleName) {
    return roles.includes(role)
}

export function hasAnyRole(roles: readonly RoleName[], allowed: readonly RoleName[]) {
    return allowed.some(role => hasRole(roles, role))
}

export const managementRoles: readonly RoleName[] = roleNames
export const reviewRoles: readonly RoleName[] = ['ADMINISTRATOR', 'REVIEWER']
export const administratorRoles: readonly RoleName[] = ['ADMINISTRATOR']
export const processingReadRoles: readonly RoleName[] = roleNames
export const processingMutationRoles: readonly RoleName[] = ['ADMINISTRATOR', 'EDITOR']
