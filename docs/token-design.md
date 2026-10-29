# Implemented token lifecycle

Access and refresh tokens are HS256 JWTs signed with separate, mandatory Base64
secrets containing at least 32 random bytes each. Exactly one active key is used
per kind. Secrets must differ; application startup rejects missing/weak keys.

Required claims: sub (user UUID), jti (unique token UUID), iat, exp and
token_type. Tokens issued here also contain nbf and email. Access tokens
carry the server-assigned role. NUTEE tokens omit issuer and audience and need no
corresponding configuration. Verification pins HS256 and checks signature,
timestamps, required identifiers and token type. Keep these keys exclusive to this
API. Google ID tokens still require Google issuer and client audience validation.
Google RS256 ID tokens
are verified separately and are never accepted as NUTEE bearer credentials.

Register/login stores the current signed refresh JWT in users.refresh_token, the
only persisted refresh field. Each user has one current refresh token; a new login
replaces the previous value. The signed exp claim is an absolute deadline (default
seven days), not a sliding expiry. No session ID or separate session table is used.
Refresh verifies the JWT, locks the user row, checks account activity and equality
with the stored token, and replaces the token pair atomically. The replacement
keeps the original exp. Older tokens are rejected without clearing the current
value. Clients must coordinate concurrent refreshes; only one use can succeed.

Logout clears users.refresh_token only when the supplied verified token matches
the current value. Password reset also clears the current refresh token.
A stale token cannot clear a newer login or refresh. Repeated
logout with the same unexpired token is harmless.

Access verification checks signature and claims without querying session/user
state. Access tokens remain valid until exp after logout or a new login. Endpoints
requiring current account state check it explicitly (for example, /auth/me rejects
disabled accounts). There is no HttpSession or access-token denylist table.
Frontend code may decode sub, email, exp and role for display; decoding does not
verify a signature, and the backend must verify tokens before trusting claims.

Tokens are JSON response values, not URLs or cookies. Protect refresh tokens in
the client and never log them. Cookie-based refresh authentication would require
additional CSRF policy and is not implemented. Google uses a five-minute HttpOnly
nonce cookie to bind the frontend's Google ID token to the initiating browser;
the token's nonce must match, and submission clears the cookie.

Google sign-in keys identities by verified provider + sub. Existing-email accounts
require explicit linking with an authenticated NUTEE access token and a matching
verified Google email. No Google access/refresh tokens are stored in the database;
these endpoints authenticate identity and do not request Google API permissions.

References: [RFC 7519](https://www.rfc-editor.org/rfc/rfc7519.html),
[refresh protection, RFC 9700](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14),
[Google ID-token verification](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).
