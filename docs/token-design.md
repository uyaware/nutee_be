# Implemented token lifecycle

Access and refresh tokens are HS256 JWTs signed with separate, mandatory Base64
secrets containing at least 32 random bytes each. Exactly one active key is used
per kind. Secrets must differ; application startup rejects missing/weak keys.

Required claims: iss, aud, sub (user UUID), sid (session UUID), jti (unique token
UUID), iat, exp and token_type. Tokens issued here also contain nbf. Access tokens
carry the server-assigned role. Verification pins HS256, checks signature, issuer,
audience, timestamps, required identifiers and token type. Google RS256 ID tokens
are verified separately and are never accepted as NUTEE bearer credentials.

Register/login creates a refresh_sessions row. Its expires_at is an absolute
deadline (default seven days), not a sliding expiry. Only SHA-256 of the current
refresh token is stored. Refresh verifies the refresh JWT and locks the row, checks
ownership/state/hash, and replaces the token pair atomically. A signed older token
is evidence of reuse and revokes the row. InvalidRefreshTokenException intentionally
does not roll back this revocation. Clients must coordinate concurrent refreshes.

Access verification also checks session expiry/revocation and account activity in
the database. Logout revokes the session immediately, invalidating both kinds of
token for that login. This is stateless HTTP authentication with server-side token
revocation state; there is no HttpSession. There is no access-token denylist table.

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
