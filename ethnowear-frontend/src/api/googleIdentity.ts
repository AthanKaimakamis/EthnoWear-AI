type GoogleIdentity = {
    initialize: (options: { client_id: string; nonce: string; auto_select: boolean; callback: (response: { credential: string }) => void }) => void
    renderButton: (element: HTMLElement, options: { theme: string; size: string; locale: string; width: number }) => void
    cancel: () => void
    disableAutoSelect: () => void
}
declare global { interface Window { google?: { accounts: { id: GoogleIdentity } } } }
let loading: Promise<GoogleIdentity> | null = null
export function loadGoogleIdentity(): Promise<GoogleIdentity> {
    if (window.google) return Promise.resolve(window.google.accounts.id)
    if (!loading) loading = new Promise<GoogleIdentity>((resolve, reject) => {
        const script = document.createElement('script')
        script.src = 'https://accounts.google.com/gsi/client'
        script.async = true
        const timer = setTimeout(fail, 15000)
        function fail() { clearTimeout(timer); script.remove(); loading = null; reject(new Error('GOOGLE_UNAVAILABLE')) }
        script.onerror = fail
        script.onload = () => {
            clearTimeout(timer)
            if (window.google) resolve(window.google.accounts.id)
            else fail()
        }
        document.head.appendChild(script)
    })
    return loading
}
