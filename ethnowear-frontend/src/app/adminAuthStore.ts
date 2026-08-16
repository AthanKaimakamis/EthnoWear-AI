let adminAuthorization: string | null = null

export function getAdminAuthorization() {
    return adminAuthorization
}

export function setAdminCredentials(username: string, password: string) {
    adminAuthorization = `Basic ${window.btoa(`${username}:${password}`)}`
}

export function clearAdminCredentials() {
    adminAuthorization = null
}
