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

The application automatically applies Flyway V1 and V2 before JPA validates its
mappings (`ddl-auto: validate`). It does not use Hibernate to create tables.

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
| POST /api/v1/auth/register | username, email, password, optional fullName | 201 |
| POST /api/v1/auth/login | identifier (email or username), password | 200 |
| POST /api/v1/auth/refresh | refreshToken; rotates the token pair | 200 |
| POST /api/v1/auth/logout | refreshToken; revokes that login session | 204 |
| GET /api/v1/auth/me | Access bearer token required | 200 |
| GET /api/v1/auth/google/nonce | Browser nonce + HttpOnly nonce cookie | 200 |
| POST /api/v1/auth/google | idToken, with the nonce cookie | 200 |
| POST /api/v1/auth/google/link | idToken + nonce cookie + access bearer | 200 |

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

Username: 3-50 ASCII letters, digits or underscores. Email and username identities
are case-insensitive. Password: at least 12 characters, at most 72 UTF-8 bytes
(BCrypt's limit), stored using BCrypt strength 12. Roles are assigned by the server;
registration cannot grant ADMIN. Local email starts unverified; email verification
and password reset are separate future features. Google-only accounts have no
local password. Disabled users cannot login, refresh, or use existing access tokens.

Errors use application/problem+json: 400 validation, 401 invalid credentials or
token, 403 forbidden, 409 identity conflict. Validation reports fields/messages,
never passwords or rejected values. No JPA entities are returned by controllers.

## HMAC token configuration

| Variable | Purpose / default |
| --- | --- |
| JWT_ACCESS_SECRET | Mandatory Base64 encoding of at least 32 random bytes |
| JWT_REFRESH_SECRET | Mandatory different Base64 secret, at least 32 random bytes |
| JWT_ISSUER | Token issuer identifier; nutee-backend |
| JWT_AUDIENCE | Receiving API identifier; nutee-api |
| ACCESS_TOKEN_TTL | 15m |
| REFRESH_TOKEN_TTL | 7d absolute session lifetime |

Both token kinds use HS256 with exactly one secret per kind. There is no external
issuer, JWK URL, or JWT_ENABLED switch for NUTEE tokens anymore. Google ID tokens
are separately verified against Google's RSA public keys. Refresh JWTs cannot be
used as access bearer tokens, and access JWTs cannot renew sessions.

Refresh sessions store a SHA-256 hash of the current signed refresh token, never
the token itself. Rotation uses a database row lock. Reuse of an older valid token
revokes the whole session, including its access tokens. Concurrent refresh requests
are therefore treated as reuse: clients must serialize refresh calls and replace
the old pair. Refresh does not extend the original seven-day deadline. Logout
revokes only that login session; other device/login sessions stay active.

Every access authentication validates JWT claims/signature and checks that its DB
session/user are active. This enables immediate logout/revocation while keeping
HTTP authentication stateless: no servlet sessions, form login or HTTP Basic.
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

V1 creates all 24 source ERD tables; V2 adds refresh_sessions for authentication.
See [schema decisions](docs/schema-decisions.md) for the explicit SQL naming/type
corrections and preserved cart/order key limits. Applied migrations must never be
edited. Add a new version for changes; do not run clean, reset the volume or enable
automatic baselining on an existing database to hide schema mismatches.

The base package is com.nutee.backend. `auth` owns controllers/DTOs/services,
provider links and refresh sessions; `user` owns the user entity/repository;
`security` owns token verification, BCrypt and CORS; `common` owns shared error
and pagination DTOs; `config` owns OpenAPI. Transaction boundaries are in services,
dependencies use constructor injection, and persistence entities stay out of APIs.
Domain tables without implemented behavior do not receive empty Java classes.

Datasource: DB_URL, DB_USERNAME, DB_PASSWORD; optional Hikari sizing variables are
DB_POOL_MAX_SIZE / DB_POOL_MIN_IDLE. SERVER_PORT defaults to 8080. Deployments must
provide database credentials and HMAC keys from a secret store; do not reuse test
keys. API docs are disabled outside local. Configure HTTPS and rate limits at the
deployment gateway before exposing login endpoints publicly. Compose is a local
database setup bound to loopback. Expired/revoked session records can be purged by
an operations job after the retention policy is chosen; validity checks do not
depend on that cleanup. No initial admin or demo credentials are seeded.

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
claims/signatures/algorithm, rotation, concurrent refresh, committed reuse revocation,
logout, disabled users, absolute expiry, Google account creation/linking/nonce,
Google RSA signature/claims validation, CORS, ProblemDetail, pagination and Swagger.
Google verification tests use a trusted local test JWK server; actual Google login
must be tested with your own configured Client ID and browser account.

Verified on 2026-10-28 with `mvnw clean verify`: 17 unit/web tests and 21 integration
tests passed (38 total; no failures, errors or skipped tests). Flyway V1/V2 were
also applied to the local nutee database. Live HTTP registration, email login,
profile, refresh and logout passed; the temporary test account was removed.

Executable JAR: `target/backend-0.0.1-SNAPSHOT.jar`. With environment variables set,
run `java -jar target/backend-0.0.1-SNAPSHOT.jar` for deployment.
