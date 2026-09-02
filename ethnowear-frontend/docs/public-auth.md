# Optional Public Google Login

Public authentication is isolated from management login. `PublicAuthApi` uses
relative same-origin URLs, cookie credentials, and an in-memory CSRF token.
It never uses the management HTTP helper, JWT, or role store.

Serve the frontend and `/api` through the same host. Production requires HTTPS
and the backend's secure cookie configuration. Register that frontend origin
with Google Identity Services. The backend provides the client ID through
`/api/public/auth/config`; disabled login is hidden and browsing stays public.

The personal-account control loads Google's official script only when opened.
A fresh backend challenge supplies its nonce. Google credentials are passed
directly to the backend and never persisted. Profile restoration uses `/me`,
not browser-stored identity claims. Reload obtains a new CSRF token.

Logout revokes only this public session and clears only public profile/chat
cache families. It leaves management and public catalogue caches intact.
No conversation endpoints or guest-history merging are implemented here.

Verification: `npm test` and `npm run build`. Real Google sign-in additionally
requires enabled backend configuration and a registered Google client.
