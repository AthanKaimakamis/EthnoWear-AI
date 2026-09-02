# Public Google Authentication

Management authentication is unchanged. Public accounts have no management roles,
passwords or relationship to management Users. Matching email addresses never link
accounts. Google issuer + subject is the only external identity key.

## Configuration

Create a Google Cloud OAuth **Web application** client and configure the exact
frontend JavaScript origins (for example http://localhost:5173). This uses Google
Identity Services' JavaScript credential callback, not an OAuth redirect callback.
No client secret, Google API access token or refresh token is required or stored.

```dotenv
ETHNOWEAR_PUBLIC_AUTH_ENABLED=true
ETHNOWEAR_GOOGLE_CLIENT_ID=YOUR_WEB_CLIENT_ID.apps.googleusercontent.com
ETHNOWEAR_PUBLIC_AUTH_ORIGINS=http://localhost:5173
ETHNOWEAR_PUBLIC_AUTH_SECURE_COOKIES=false
ETHNOWEAR_PUBLIC_SESSION_TTL=7d
```

`secure-cookies=false` is allowed only with localhost origins. Production requires
HTTPS and secure cookies. Frontend and backend must be same-site (prefer a same-origin
reverse proxy) because cookies use SameSite=Lax. An unrelated cross-site deployment
is intentionally unsupported. Never use a wildcard credentialed CORS origin.

Login is disabled by default until a real client ID is configured. The API does not
contact Google at startup. Signature keys use Google's fixed HTTPS JWKS endpoint,
cached by Nimbus; connection/read timeouts are five seconds.

## Frontend Contract

Use a separate public-auth client/store, with `credentials: "include"`. Do not attach
management Authorization headers, store Google credentials, or copy public auth into
the management state. All responses are non-cacheable.

1. `GET /api/public/auth/config` returns `{enabled, googleClientId}`. Hide/disable
   Google login when disabled; public browsing and guest chat remain available.
2. `GET /api/public/auth/csrf` returns `{headerName: "X-PUBLIC-CSRF", token}` and sets
   an HttpOnly CSRF cookie. Keep the response token in memory and send it as that
   header on public POST/PUT/PATCH/DELETE requests. Fetch it again after page reload.
3. `POST /api/public/auth/google/challenge` with the CSRF header returns
   `{nonce, expiresAt}` and sets an HttpOnly challenge cookie. Pass nonce and
   googleClientId to `google.accounts.id.initialize`, using its credential callback.
   The challenge expires after five minutes and succeeds only once.
4. In that callback, `POST /api/public/auth/google` with JSON
   `{ "credential": response.credential }`, the CSRF header, and credentials included.
   The API verifies Google's signature, issuer, audience, expiry, verified email,
   authorized party when present, and nonce. It returns `{userId, displayName, email}`
   and sets `ETHNOWEAR_PUBLIC_SESSION` (HttpOnly, SameSite=Lax, seven-day default).
   userId is a public UUID, not a management ID. Never persist the Google credential.
5. `GET /api/public/auth/me` restores the profile after reload. A 401 means no active
   public session. Management login does not satisfy this endpoint.
6. `POST /api/public/auth/logout` with CSRF revokes this device's public session and
   clears public login cookies. It is idempotent, preserves history, and does not
   sign out management or other devices. Clear only public profile/chat caches.

Sessions are opaque random secrets; only hashes are stored in SQL. Authentication
checks expiry, revocation and Enabled on every request. Reauthentication rotates the
current session while other devices remain signed in. Guest history is not silently
merged into an account. Conversation persistence now uses PublicUserId, but chat
history/execution endpoints are a subsequent stage and are not delivered here.

The future conversation chain `/api/conversations/**` uses the same public cookie
and CSRF contract. Ownership must be resolved server-side from PublicUserPrincipal
or a validated guest session; never accept owner IDs from request bodies. Management
JWTs are not interpreted there. No public session authorizes `/api/admin/**`.

## Errors

| HTTP | Code | Action |
|---|---|---|
| 400 | PUBLIC_AUTH_VALIDATION_FAILED | Correct invalid request; safe field messages are included |
| 401 | GOOGLE_CREDENTIAL_INVALID | Restart Google sign-in |
| 401 | PUBLIC_LOGIN_CHALLENGE_INVALID | Obtain a new challenge and Google credential |
| 401 | PUBLIC_AUTH_REQUIRED | Treat as signed out |
| 403 | PUBLIC_ACCOUNT_DISABLED | Display account unavailable |
| 403 | PUBLIC_ACCESS_DENIED | Refetch CSRF once; do not retry indefinitely |
| 429 | PUBLIC_LOGIN_RATE_LIMITED | Respect Retry-After |
| 503 | PUBLIC_LOGIN_DISABLED | Sign-in is not configured |
| 503 | PUBLIC_LOGIN_BUSY | Retry sign-in shortly |
| 503 | GOOGLE_VERIFICATION_UNAVAILABLE | Google verification is temporarily unavailable |
| 503 | PUBLIC_AUTH_UNAVAILABLE | API authentication is temporarily unavailable |

Untrusted origins are rejected by CORS before controller execution. Do not expect a
readable JSON body for those browser failures. The login limiter is per API instance
and socket peer address, 30 Google-auth POSTs/minute; configure a trusted reverse
proxy's rate limiting for internet-facing deployment rather than trusting arbitrary
forwarded headers.

Official references: [Google token verification](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token)
and [Google JavaScript API](https://developers.google.com/identity/gsi/web/reference/js-reference).
