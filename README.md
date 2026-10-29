# NUTEE Backend

Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.14, PostgreSQL 16+, Flyway,
Spring Data JPA, Spring Security, Jakarta Validation, Springdoc 2.10.0, Lombok,
JUnit 5, Mockito and PostgreSQL Testcontainers. Spring Boot manages dependency versions.

This modular monolith implements database migrations and authentication. Product,
cart, order, payment, shipping and after-sales APIs are not part of this delivery.

## Run locally on Windows

Start Docker Desktop with its Linux-container engine, then run from this directory:

```powershell
.\scripts\init-local.ps1
.\scripts\run-local.ps1
```

The initializer creates/reuses `.env`, generates separate random HMAC secrets,
preserves existing database credentials, and starts PostgreSQL. Default DB settings:

| Setting | Value |
| --- | --- |
| Host / port | localhost:5433 |
| Database / user | nutee / nutee |
| Password | DB_PASSWORD in ignored `.env` |

The runner imports `.env`, finds JDK 21, and starts the local profile. If necessary:
`.\scripts\run-local.ps1 -JdkHome 'C:\path\to\jdk-21'`.
The default JDK 17 installed on this machine is not used by the runner. Ctrl+C stops
the API. `docker compose down` stops PostgreSQL and preserves the data volume.
Never overwrite `.env` with the template after initialization: that would replace
configuration needed by the existing database. Changing POSTGRES_PASSWORD does not
change an already initialized PostgreSQL role's password.

The application automatically applies Flyway V1 before JPA validates its
mappings (`ddl-auto: validate`). It does not use Hibernate to create tables.
V1 includes `users.refresh_token`; the earlier V2 refresh-session migration was
removed. Existing databases that applied the old V1/V2 need an explicit schema
and Flyway-history reconciliation before startup; this source change does not
reset or modify existing database data.

| URL | Purpose |
| --- | --- |
| http://localhost:8080/api/v1/swagger-ui.html | Swagger UI, local profile |
| http://localhost:8080/api/v1/actuator/health | Public status without component details |
| http://localhost:8080/api/v1/actuator/health/readiness | Readiness including the DB |

For a manual/Bash setup, copy `.env.example` only if `.env` is absent, fill in
database credentials and distinct random Base64 HMAC secrets, and export the
variables into your shell/IDE. Java does not import `.env` automatically. Then run
`docker compose up -d --wait` and `./mvnw spring-boot:run -Dspring-boot.run.profiles=local`.
On Unix, ensure the generated wrapper is executable (`chmod +x mvnw`).

## Authentication API

All POST bodies below are JSON. Register/login/Google sign-in return a token pair
and a safe user DTO; `expiresIn` / `refreshExpiresIn` are seconds.
Tokens are returned in JSON with Cache-Control: no-store; there are no token URLs
or authentication cookies. Do not send an expired Authorization header to the
public refresh/logout endpoints, as bearer authentication will reject it first.

| Method / path | Body / purpose | Status |
| --- | --- | --- |
| POST /api/v1/auth/register | email, password; optional username, fullName | 201 |
| POST /api/v1/auth/login | identifier (email or username), password | 200 |
| POST /api/v1/auth/refresh | refreshToken; rotates the token pair | 200 |
| POST /api/v1/auth/logout | refreshToken; clears the matching user refresh token | 204 |
| GET /api/v1/auth/me | Access bearer token required | 200 |
| GET /api/v1/auth/google/nonce | Browser nonce + HttpOnly nonce cookie | 200 |
| POST /api/v1/auth/google | idToken, with the nonce cookie | 200 |
| POST /api/v1/auth/google/link | idToken + nonce cookie + access bearer | 200 |
| POST /api/v1/auth/forgot-password | email; generic response, sends reset email | 200 |
| POST /api/v1/auth/reset-password | token, password; consumes reset link | 204 |

Example PowerShell:

```powershell
$base = 'http://localhost:8080/api/v1'
# Choose your own username/email/password. Do not commit real credentials.
$body = @{
    username = 'my_username'
    email = 'your-address@example.com'
    password = 'replace-with-your-own-password'
    fullName = 'Your Name'
} | ConvertTo-Json
$session = Invoke-RestMethod "$base/auth/register" -Method Post -ContentType application/json -Body $body

$login = @{ identifier = 'my_username'; password = 'replace-with-your-own-password' } | ConvertTo-Json
$session = Invoke-RestMethod "$base/auth/login" -Method Post -ContentType application/json -Body $login
Invoke-RestMethod "$base/auth/me" -Headers @{ Authorization = "Bearer $($session.accessToken)" }

# Replace the entire pair after every successful refresh. Serialize refresh calls.
$body = @{ refreshToken = $session.refreshToken } | ConvertTo-Json
$session = Invoke-RestMethod "$base/auth/refresh" -Method Post -ContentType application/json -Body $body
$body = @{ refreshToken = $session.refreshToken } | ConvertTo-Json
Invoke-RestMethod "$base/auth/logout" -Method Post -ContentType application/json -Body $body
```

Username: generated when omitted, or 3-50 ASCII letters, digits or underscores. Email and username identities
are case-insensitive. Password: at least 12 characters, at most 72 UTF-8 bytes
(BCrypt's limit), stored using BCrypt strength 12. Roles are assigned by the server;
registration cannot grant ADMIN. Local email starts unverified; email verification
is a separate future feature. Google-only accounts have no
local password. Disabled users cannot login, refresh, or fetch their profile.
Bearer verification uses signed claims; endpoints needing current account state
must check it explicitly, as `/auth/me` does.

Errors use application/problem+json: 400 validation, 401 invalid credentials or
token, 403 forbidden, 409 identity conflict. Validation reports fields/messages,
never passwords or rejected values. No JPA entities are returned by controllers.

## HMAC token configuration

| Variable | Purpose / default |
| --- | --- |
| JWT_ACCESS_SECRET | Mandatory Base64 encoding of at least 32 random bytes |
| JWT_REFRESH_SECRET | Mandatory different Base64 secret, at least 32 random bytes |
| ACCESS_TOKEN_TTL | 15m |
| REFRESH_TOKEN_TTL | 7d absolute refresh lifetime |

NUTEE JWTs omit `iss` and `aud`; no JWT_ISSUER/JWT_AUDIENCE configuration is needed.
Use dedicated keys for this API, without sharing them with other services.
Both token kinds use HS256 with exactly one secret per kind. There is no external
issuer, JWK URL, or JWT_ENABLED switch for NUTEE tokens anymore. Google ID tokens
are separately verified against Google's RSA public keys. Refresh JWTs cannot be
used as access bearer tokens, and access JWTs cannot renew sessions.

Each user stores one current signed JWT in `users.refresh_token` (TEXT, nullable).
Register/login replaces it; a new login invalidates the previous refresh token.
Refresh verifies the signature, token type and expiry, locks the user row, checks
the stored value, and replaces the token pair. Older tokens are rejected without
clearing the current token. Clients must serialize refresh calls and replace the
old pair. Refresh preserves the original seven-day deadline from the JWT `exp`.
Logout clears only a matching current refresh token; stale logout requests do
not clear a newer login's token.

Access authentication validates JWT claims/signature without a session lookup.
JWTs include `sub` (user ID), `email`, `exp`, and access tokens also include `role`.
The frontend can decode these claims for display; the backend verifies them before
trusting them. Logout does not invalidate issued access tokens: they remain usable
until expiry (default 15 minutes). There are no servlet sessions, form login or HTTP Basic.
There are no token cookies, so CSRF is disabled for these JSON/bearer endpoints.
The Google flow additionally binds its ID token to a browser nonce cookie.
See [token design](docs/token-design.md) for details.

## TODO: enable Google sign-in

1. Create/select a project in [Google Cloud Console](https://console.cloud.google.com/).
2. Configure the Google Auth consent screen, audience/test users as applicable.
3. Create an OAuth Client ID of type **Web application**.
4. Add the exact frontend Authorized JavaScript origins, e.g. http://localhost:3000
   and http://localhost for local testing, as described in Google's setup guide.
5. Set `GOOGLE_CLIENT_ID=<your-client-id>.apps.googleusercontent.com` and
   `GOOGLE_AUTH_ENABLED=true` in `.env`, then restart the backend.
6. Set `CORS_ALLOWED_ORIGINS` to the same exact trusted frontend origins (comma-separated).
7. Set the same Client ID as `VITE_GOOGLE_CLIENT_ID` in `frontend/.env.local`, then restart Vite.

This is the Google Identity Services JavaScript ID-token flow. It needs a Client
ID, not an API key or a client secret, and has no backend OAuth redirect URI.
Google remains disabled until configured; Google endpoints return a clear 503
while local email/username authentication continues working.

Load `https://accounts.google.com/gsi/client` in your frontend, then:

```javascript
const api = 'http://localhost:8080/api/v1';
const response = await fetch(`${api}/auth/google/nonce`, { credentials: 'include' });
if (!response.ok) throw new Error('Google sign-in is unavailable');
const { nonce } = await response.json();
google.accounts.id.initialize({
  client_id: 'YOUR_GOOGLE_CLIENT_ID.apps.googleusercontent.com',
  nonce,
  callback: async ({ credential }) => {
    const result = await fetch(`${api}/auth/google`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ idToken: credential })
    });
    if (!result.ok) throw new Error('Google sign-in failed');
    const session = await result.json(); // Handle privately; never log tokens.
    // Keep the access token in memory and send it as a Bearer token to protected APIs.
  }
});
google.accounts.id.renderButton(document.getElementById('google-sign-in'), { theme: 'outline' });
```

Fetch a new nonce before each sign-in attempt; it expires after five minutes and
the cookie is cleared on submission. The backend verifies Google signatures,
issuer, audience/client ID, timestamps, email_verified, sub and nonce. Provider
identity uses Google's stable sub, not the email. A new identity creates a customer;
an existing identity signs in to its associated user. An existing email is not
automatically linked. Sign in locally, obtain a fresh Google nonce/credential, and
POST `/auth/google/link` with your access bearer token to link the matching email.

This browser example assumes frontend and API are same-site (localhost with
different ports locally, or app.example.com/api.example.com in production). The
nonce cookie uses SameSite=Lax and HttpOnly. For production, use HTTPS and
`AUTH_SECURE_COOKIE=true`; the application defaults to secure cookies outside local.
Cross-site frontend/API deployments require a separately reviewed cookie policy.

Google setup references: [create a client ID](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid),
[server verification](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

## Schema, architecture and operations

### Password recovery and frontend connection

The frontend uses `/api/v1/auth` for registration, login, Google login, current
user, refresh and logout. Vite proxies `/api/v1` to localhost:8080; production needs
the same reverse proxy or an explicit `VITE_API_BASE_URL` plus backend CORS origins.
The catalog/cart/profile/order modules still use their existing local adapters.

Flyway V2 adds only `users.password_reset_hash` and `password_reset_expires_at`;
V1 is unchanged. Reset links contain a random 256-bit token, stored only as SHA-256,
expire after 15 minutes, and are consumed atomically with a user-row lock. A new
link replaces the old link; requests for the same account have a 60-second cooldown.
Unknown, disabled and Google-only accounts receive the same public response.
Successful reset changes the BCrypt password and clears the refresh token.
Existing access JWTs remain valid until their normal expiry.

For local email testing, `docker compose up -d mailpit` starts SMTP on localhost:1025
and the inbox at http://localhost:8025. It is optional and started separately.
The application defaults target Mailpit. Production must set MAIL_HOST, MAIL_PORT,
MAIL_USERNAME, MAIL_PASSWORD, MAIL_SMTP_AUTH, MAIL_STARTTLS and MAIL_FROM for the
chosen SMTP provider. Set PASSWORD_RESET_URL to the frontend's trusted reset page;
it must not contain a query or fragment. Tokens are appended in the URL fragment.
Email delivery failures are logged without tokens and preserve the generic response.
Password recovery defaults to disabled (`PASSWORD_RESET_ENABLED=false`). Set it
to true only after local Mailpit or a real SMTP provider is ready; disabled recovery
returns 503 without looking up accounts or sending mail.
Apply gateway rate limits to login, forgot-password and reset-password endpoints.

V1 creates all 24 source ERD tables and includes users.refresh_token for authentication.
See [schema decisions](docs/schema-decisions.md) for the explicit SQL naming/type
corrections and preserved cart/order key limits. Applied migrations must never be
edited. Add a new version for changes; do not run clean, reset the volume or enable
automatic baselining on an existing database to hide schema mismatches.

The base package is com.nutee.backend. `auth` owns controllers/DTOs/services,
provider links and refresh-token flows; `user` owns the user entity/repository;
`security` owns token verification, BCrypt and CORS; `common` owns shared error
and pagination DTOs; `config` owns OpenAPI. Transaction boundaries are in services,
dependencies use constructor injection, and persistence entities stay out of APIs.
Domain tables without implemented behavior do not receive empty Java classes.

Datasource: DB_URL, DB_USERNAME, DB_PASSWORD; optional Hikari sizing variables are
DB_POOL_MAX_SIZE / DB_POOL_MIN_IDLE. SERVER_PORT defaults to 8080. Deployments must
provide database credentials and HMAC keys from a secret store; do not reuse test
keys. API docs are disabled outside local. Configure HTTPS and rate limits at the
deployment gateway before exposing login endpoints publicly. Compose is a local
database setup bound to loopback. Refresh expiry is enforced from the signed JWT,
so an expired value stored on a user cannot renew tokens. No initial admin or demo
credentials are seeded.

## Build and tests

```powershell
.\mvnw.cmd test
.\mvnw.cmd clean verify
```

Use JDK 21. `test` runs unit/web/cryptography tests without Docker. `verify` also
packages the executable JAR and runs *IT tests on isolated PostgreSQL 16 containers.
Docker absence fails verification rather than silently skipping integration tests.
Test configuration uses non-production keys and never connects to the local DB.

Coverage includes schema migration/JPA validation, FK integrity, BCrypt, registration,
email/username login, duplicate identities, access/refresh key separation, invalid
claims/signatures/algorithm, rotation, concurrent refresh, rejection of stale tokens,
logout, disabled users, absolute expiry, Google account creation/linking/nonce,
Google RSA signature/claims validation, CORS, ProblemDetail, pagination and Swagger.
Google verification tests use a trusted local test JWK server; actual Google login
must be tested with your own configured Client ID and browser account.

The previous V1/V2 implementation was verified on 2026-10-28. Those results do not
validate the current single-field refresh-token schema. Use the commands above to
verify this implementation against isolated test databases.

Executable JAR: `target/backend-0.0.1-SNAPSHOT.jar`. With environment variables set,
run `java -jar target/backend-0.0.1-SNAPSHOT.jar` for deployment.
