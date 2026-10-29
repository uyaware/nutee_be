# Implemented token lifecycle

Access and refresh tokens are HS256 JWTs signed using separate mandatory Base64
keys of at least 32 random bytes. Keys must differ and remain exclusive to NUTEE.
Required claims: sub (user UUID), jti (token UUID), iat, exp and token_type.
Tokens also contain nbf/email; access tokens include the server-assigned role.
NUTEE omits iss/aud. Google identity verification separately validates its own
issuer/audience and RSA signature.

## Browser transport

The backend sets accessToken and refreshToken in HttpOnly, host-only cookies with
Path=/api/v1, SameSite=Lax by default and Secure=true outside local HTTP config.
Remembered cookies receive Max-Age bounded by signed token expiry; otherwise they
are browser-session cookies. Both cookies accompany API requests automatically
when fetch uses credentials:include. JWTs are not returned in JSON, exposed to
JavaScript, stored in browser storage, sent in Authorization or posted in bodies.
Login/register/Google/refresh return only user and lifetime metadata.

A browser has one cookie session shared by tabs and both portals. Login replaces
the current browser account. Logout clears access/refresh/remember cookies and
both portal views. /auth/refresh and /auth/logout read refreshToken from the
request cookie and accept POST without a body. Expired access cookies are ignored
on public auth endpoints, so they cannot block refresh or logout.

## CSRF and frontend coordination

All unsafe HTTP methods require CSRF, including public auth mutations. The
HttpOnly CSRF cookie stores the expected token. GET /api/v1/auth/csrf returns
token/headerName for the client header and does not create a servlet session.
Login/register/Google login/logout clear the old CSRF cookie. Missing/invalid
CSRF returns 403 with code=CSRF_INVALID. Credentialed CORS only allows configured
origins and X-XSRF-TOKEN; no wildcard origins are allowed.

The shared frontend API client always includes cookies, attaches CSRF for mutations,
refreshes after 401 and retries a protected request once. A queue and Web Locks
serialize refresh and login/logout across requests/tabs. It probes /auth/me inside
the lock to avoid rotating again when another tab already refreshed. A CSRF_INVALID
response reacquires CSRF and retries once; network errors do not trigger refresh.

## Server state

Each user stores one current signed refresh JWT in users.refresh_token. Login
replaces it. Refresh verifies signature/type/expiry, locks the user, checks activity
and equality with the stored JWT, and replaces the pair atomically. Refresh keeps
the original exp; rotation cannot extend the absolute seven-day deadline.
Older tokens cannot refresh or clear a newer login's stored refresh token.

Logout clears only a matching stored refresh. Missing/expired refresh cookies can
still log out and clear browser cookies. Invalid refresh returns 401 and clears
browser auth cookies. Password reset clears the stored refresh token.

Access validation checks signed claims without a user/session lookup. An issued
access JWT remains valid until exp after logout/reset/new login. APIs needing live
account state must check it explicitly, as /auth/me does for disabled accounts.

Google identities use provider + verified sub. Existing-email accounts require
explicit linking using their authenticated access cookie and matching verified
Google email. No Google access/refresh tokens are stored. A five-minute HttpOnly
nonce cookie binds the ID token to the browser; submission clears it.

References: [JWT RFC 7519](https://www.rfc-editor.org/rfc/rfc7519.html),
[Spring CSRF](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html),
[Google verification](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).
