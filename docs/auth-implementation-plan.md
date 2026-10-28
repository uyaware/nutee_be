# NUTEE initialization, login, RBAC, and token implementation plan

> Historical proposal. The subsequent user request specifies email/username and
> Google sign-in, HS256 access and refresh JWTs with separate HMAC secrets, and
> implementation of all ERD tables. Those choices supersede the RS256/opaque-token,
> email-only and deferred-social-login proposals below. See [README](../README.md),
> [implemented token lifecycle](token-design.md) and [schema decisions](schema-decisions.md)
> for the delivered behavior. Other proposals below remain future work.

Date: 2026-10-28. Status: detailed implementation proposal; application code and database have not been changed.

The user confirmed email/password website login with CUSTOMER, STAFF, and ADMIN roles. Remaining policy choices below are proposed defaults, not claims that the features already exist.

## 1. Outcome and scope

Deliver a working Spring Boot authentication slice on the existing backend: persistent users, customer registration, separate customer/backoffice login, signed access JWTs, opaque rotating refresh tokens, logout, role enforcement, account disablement, administrator bootstrap, API documentation, and meaningful automated tests.

Keep the current modular monolith and package-by-feature organization. Catalog, checkout, payments, social login, email delivery, password recovery, MFA, and configurable permissions are separate milestones. Review their schema now, then introduce their tables when the corresponding feature is implemented.

Planning deliverables are this file and [the schema review and exact 24-table inventory](schema-review.md). This planning task does not start the auth implementation.

## 2. Findings from the repository and ERD

| Area | Observed state | Work needed |
| --- | --- | --- |
| Backend | Java 21, Spring Boot 3.5.16, Maven Wrapper, PostgreSQL 16, Spring Data JPA, Flyway | Extend the existing scaffold; verify resolved dependencies and build before changing it |
| API conventions | /api/v1, DTO validation, RFC 9457 ProblemDetail, pagination helpers | Apply consistently to authentication |
| Security | Stateless filter chain, method security, BCrypt strength 12; JWT verification optional | Password authentication, internal token issuance, live account/session validation, role conversion |
| JWT | Decoder uses trusted remote JWK URI; validates issuer/audience/timestamps | Add an internal signer and local public-key decoder; no self-HTTP JWK dependency |
| Tokens | 15-minute / 7-day properties prepared | Implement issuance, persistence, rotation, expiry, revocation and browser transport |
| Domain persistence | No production domain SQL migrations or JPA entities found | Add reviewed auth migrations and mappings together |
| Tests | Infrastructure/JWT tests and PostgreSQL Testcontainers ITs exist | Preserve applicable checks; replace assertions that require zero entities/domain tables |
| Frontend | Existing React/Vite app with separate customer and backoffice mock sessions | Preserve portal behavior and replace auth mock through an HTTP adapter in a later frontend change |
| Roles | User.role exists; frontend uses customer/staff/admin | One constrained persisted role per account; static policies first |
| ERD | 24 tables; User includes password, role, is_active, email_verified and timestamps | Add session/token history; clarify user credential/identity constraints |
| Runtime | Docker access denied during this planning session | Live DB schema, migrations applied and runtime health remain unverified |

The README records an earlier successful build, but that is historical evidence. No test suite was rerun for these documentation-only changes.

## 3. Proposed decisions

| Topic | Initial decision |
| --- | --- |
| Authentication | Email + password; no username login in the first slice |
| Role model | Exactly one of CUSTOMER, STAFF, ADMIN per user |
| Registration | Public registration creates CUSTOMER only; full name/phone can be completed later |
| Email verification | email_verified defaults false; permit initial login; verified-only actions are added with the email milestone |
| Portal policy | CUSTOMER uses customer portal; STAFF/ADMIN use backoffice; no implicit ADMIN-to-CUSTOMER hierarchy |
| Access token | RS256 JWT, 15-minute lifetime, Authorization header only |
| Refresh token | At least 32 SecureRandom bytes, base64url encoded, opaque; SHA-256 digest persisted |
| Session lifetime | Seven-day absolute login lifetime; rotation does not extend the original deadline |
| Multi-device | Separate session family per device/login; no arbitrary device cap in the MVP |
| Revocation | Check user and session state on each authenticated request; logout/disablement affect subsequent requests |
| Browser storage | Access token in memory; refresh token in an HttpOnly cookie; no credentials/tokens in localStorage |
| Browser topology | Prefer same-site HTTPS with a reverse proxy and /api routing; explicit local frontend origins |
| Refresh races | Strict single-use rotation; frontend serializes refresh across requests/tabs |
| Administrator creation | Explicit one-time operator bootstrap; no public privileged registration or fixed production password |

These are application-owned login endpoints. They are not an implementation of a complete OAuth/OIDC authorization server. If third-party clients, federation, consent, or OIDC discovery become requirements, revisit that boundary before building protocol endpoints.

## 4. Database design for the auth slice

Use lowercase snake_case physical names such as users. Preserve UUID user IDs. Avoid quoted SQL names such as User and Order.

### 4.1 users

| Field | Proposed SQL contract | Purpose |
| --- | --- | --- |
| id | UUID PRIMARY KEY | Stable identity; JWT sub |
| email | VARCHAR(255) NOT NULL | Canonical email used for login |
| username | VARCHAR(255), nullable | Optional profile handle; not required by the existing registration form |
| password_hash | VARCHAR(255) NOT NULL for password-only MVP | Rename diagram password; never store plaintext |
| full_name | VARCHAR(100), nullable | Supports profile completion after email/password registration |
| phone | VARCHAR(32), nullable | Replaces diagram width 10; supports international representation |
| image_url | TEXT, nullable | Existing profile field |
| role | VARCHAR(16) NOT NULL DEFAULT 'CUSTOMER' | CHECK role IN ('CUSTOMER','STAFF','ADMIN') |
| is_active | BOOLEAN NOT NULL DEFAULT true | Account enablement |
| email_verified | BOOLEAN NOT NULL DEFAULT false | Verification fact |
| token_version | BIGINT NOT NULL DEFAULT 0 | Account-wide token invalidation; CHECK >= 0 |
| password_changed_at | TIMESTAMPTZ, nullable | Credential change metadata |
| last_login_at | TIMESTAMPTZ, nullable | Successful password login metadata |
| created_at | TIMESTAMPTZ NOT NULL | Creation timestamp |
| updated_at | TIMESTAMPTZ NOT NULL | Last mutation timestamp |

Enforce a case-insensitive unique email index, for example UNIQUE(lower(email)), and use the same trimming/normalization rules in every write and login lookup. Decide accepted email character/length policy explicitly. If username is supported as an editable handle, add a matching unique index for non-NULL handles.

Do not require profile fields at registration unless the UI contract changes. Do not allow mass assignment of role, is_active, email_verified, token_version, or timestamps.

For the new password-only database, password_hash is required. A later provider-only account design may make it nullable or extract a credential table; perform that intentionally with a migration.

### 4.2 auth_sessions

Each login creates one session family. The persisted session is application security state; Spring must still avoid creating an HTTP session or JSESSIONID.

| Field | Proposed SQL contract | Purpose |
| --- | --- | --- |
| id | UUID PRIMARY KEY | Session family ID; JWT sid |
| user_id | UUID NOT NULL REFERENCES users(id) | Session owner |
| portal | VARCHAR(16) NOT NULL | CHECK IN ('CUSTOMER','BACKOFFICE') |
| created_at | TIMESTAMPTZ NOT NULL | Original login time |
| last_refreshed_at | TIMESTAMPTZ, nullable | Last successful rotation |
| expires_at | TIMESTAMPTZ NOT NULL | Absolute seven-day deadline |
| revoked_at | TIMESTAMPTZ, nullable | Family invalidation |
| revocation_reason | VARCHAR(64), nullable | LOGOUT, LOGOUT_ALL, TOKEN_REUSE, ROLE_CHANGED, ACCOUNT_DISABLED, PASSWORD_CHANGED |
| user_agent | VARCHAR(512), nullable | Optional device display; truncate untrusted input |
| ip_address | INET, nullable | Optional audit data; trust forwarding headers only from configured proxies |

CHECK expires_at > created_at and coherent revocation fields. Index user_id and expiry/active-session lookups. Do not treat user agent/IP as proof of device identity or reject mobile users solely because IP changes. Define retention for personal metadata.

### 4.3 refresh_tokens

| Field | Proposed SQL contract | Purpose |
| --- | --- | --- |
| id | UUID PRIMARY KEY | Rotation record ID |
| session_id | UUID NOT NULL REFERENCES auth_sessions(id) | Family ownership |
| token_hash | CHAR(64) NOT NULL UNIQUE | Hex SHA-256 digest of a high-entropy opaque token |
| issued_at | TIMESTAMPTZ NOT NULL | Token issuance |
| expires_at | TIMESTAMPTZ NOT NULL | Never later than session deadline |
| consumed_at | TIMESTAMPTZ, nullable | Marks successful use; reuse is detectable |
| replaced_by_id | UUID, nullable, UNIQUE self-FK | Links to the successor rotation record |

Index session_id and expires_at. Define time checks and successor consistency; enforce same-family successor links in service or a suitable composite FK. Session revocation invalidates all its tokens without updating every history row.

A token hash lookup finds the token record; a session lock coordinates rotation and revocation. Retain consumed records until the whole family can no longer be used, plus a documented audit buffer. Do not prune a consumed predecessor while a successor remains valid.

Do not add refresh_token or a growing refresh_tokens_used array to users. Raw refresh values exist only at creation and client transport; never log them or persist them. Access JWTs are not database records.

### 4.4 auth_audit_events

Use a dedicated append-only security audit table, or an equivalent durable sink, rather than mixing login security events into product Event_Log analytics.

Proposed fields: id UUID, user_id nullable UUID FK, session_id nullable UUID FK, event_type VARCHAR(64), outcome VARCHAR(16), occurred_at TIMESTAMPTZ, correlation_id VARCHAR(64), optional IP/user-agent metadata and a bounded sanitized reason code. Unknown-user events need no plaintext email; use a protected identifier digest only if needed for abuse correlation.

Record successful/failed login, refresh reuse, logout, role/active changes, bootstrap and password changes. Limit access, redact sensitive data, define retention. Persist reuse/revocation audit even when the API rejects the request.

### 4.5 Tables deferred until their feature

Auth_Provider gets provider-subject uniqueness before social login. Reset and verification challenges get hashed tokens, purpose, expiry and consumed state when those flows are implemented. Dynamic role/permission tables are unnecessary for the agreed fixed-role MVP.

The full commerce corrections are in schema-review.md, including address FK mismatch, variant-aware line keys, review/aftersale ownership and payment idempotency.

## 5. Authentication and token behavior

### 5.1 Password handling and login

Use Spring Security password authentication with an application user lookup. Because this is a new auth system, prefer Argon2id over the scaffold's current BCrypt encoder; use at least 19 MiB memory, two iterations and parallelism one, then benchmark under realistic concurrency. Add the required crypto provider dependency and a delegating hash format. Existing real BCrypt hashes, if any are found, require verification/rehash compatibility instead of forced replacement.

Validate password input size, support long passphrases, and never silently truncate or trim passwords. If BCrypt is retained by an explicit implementation decision, enforce its UTF-8 byte limit and document it. [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html).

Normalize email; use a generic login error for unknown account, wrong password, disabled account and wrong portal. Perform an appropriate dummy hash check for unknown users to reduce obvious timing differences. Rate-limit by both client IP and normalized identifier with bounded state; do not lock arbitrary victims indefinitely based only on attacker-controlled failures.

On successful credentials, lock/recheck the user, verify role/active state and portal, create the session + first refresh hash + audit record in one transaction, and update last_login_at. Only send tokens/cookies after commit. A registration route explicitly sets CUSTOMER and ignores/rejects privileged attributes.

### 5.2 Access token

JWT header: alg RS256, typ at+jwt, kid selecting a configured key. Claims: iss, aud, sub (user UUID), sid (session UUID), jti, iat, nbf, exp, ver and portal. A role claim can be included as a snapshot for diagnostics/UI, but server authorities come from current persisted state.

Keep iss=nutee-backend and aud=nutee-api initially, configurable per environment. Strictly allow RS256; validate signature, required claims, issuer, audience, timestamps, configured clock skew, token type and claim formats. No passwords, email addresses or other unnecessary personal data in JWT claims.

Use NimbusJwtEncoder/NimbusJwtDecoder already compatible with the resource-server foundation. For the internal issuer, load a private signing key and public verification key ring directly. Replace the current mandatory remote JWT_JWK_SET_URI requirement for this mode; do not require NUTEE to call its own HTTP endpoint to verify its tokens.

Store keys outside the repository using environment-configured file locations or deployment secrets. Generate local keys once into ignored secret storage, never on every application restart. Keep the old public key during a documented rotation overlap covering outstanding access-token lifetimes and skew. Unknown kid is rejected. A public JWKS endpoint is only needed if independent consumers require one; never expose private JWK parameters.

After JWT verification, load current users/auth_sessions state and require matching user/session ownership, matching portal, active account, matching token_version, and an unexpired/unrevoked session. Build ROLE_CUSTOMER / ROLE_STAFF / ROLE_ADMIN from the current database role. Unknown/malformed roles fail closed.

This intentionally makes authorization depend on server state. It gives subsequent-request revocation without a separate JWT blacklist. Database outage must not fall back to trusting stale claims. In-flight work already authorized may finish; sensitive mutations recheck state within their transaction.

### 5.3 Refresh rotation and replay detection

1. Read only the selected portal's refresh cookie; validate token size/format before hashing.
2. Hash it and find the exact token row. Random unknown tokens return a generic 401 and must not revoke unrelated sessions.
3. Acquire locks in a consistent order across security writes (user, then session); re-read token state under the family lock.
4. Check account active, role/portal eligibility, session expiry/revocation, and token expiry.
5. If consumed_at is already set, durably revoke the session family with TOKEN_REUSE and record an audit event; commit these changes before returning 401.
6. For an unused valid token, create a random successor and its digest record; atomically set predecessor consumed_at/replaced_by_id and update session last_refreshed_at.
7. Commit and return a new access token plus the replacement refresh cookie.

Never issue the successor before the transaction succeeds. Avoid a runtime exception rolling back the reuse revocation: return a committed domain outcome and map it to the HTTP error outside the transaction, or use another explicitly tested transaction boundary.

The family deadline remains the original expires_at; refresh rotation cannot create perpetual sessions. Limit access-token exp to the earlier of now + 15 minutes and the remaining family lifetime.

Strict concurrent reuse policy: at most one refresh operation can consume a predecessor. A second use revokes the family, even if caused by legitimate overlapping requests. The frontend must coordinate refresh across tabs/requests. A lost refresh response requires re-login under this policy; no transparent replay/grace recovery is promised.

Rotation, retained predecessor relationships and family revocation follow the protections described in [RFC 9700 section 4.14](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14).

### 5.4 Logout, global logout, and account changes

Current-session logout accepts the selected portal cookie, revokes the matching family and clears that cookie. It does not require a currently valid access JWT, so an expired access token cannot prevent logout. Unknown/already revoked tokens can still yield idempotent 204.

Logout-all requires a valid access JWT, increments user token_version and revokes all sessions for that user across portals/devices. Its name and UI must communicate this global behavior; ordinary portal logout only affects that portal's current family.

Disablement, role change and future password reset/change increment token_version and revoke affected sessions atomically. Re-enabling an account does not reactivate old sessions. ADMIN has no automatic customer identity; use an explicit customer account for storefront work.

Protect user administration from privilege escalation. No self-promotion, public role assignment, or accidental removal/disablement of the last active administrator. Serialize administrator-set changes using a shared DB lock/control row or equivalent, then test concurrent attempts; locking only each target user is insufficient for the last-admin invariant.

## 6. Browser transport and portal isolation

Use different refresh cookie names and narrow paths:

| Portal | Cookie | Path |
| --- | --- | --- |
| Customer | nutee_customer_refresh | /api/v1/auth/customer |
| Backoffice | nutee_backoffice_refresh | /api/v1/auth/backoffice |

Set HttpOnly, Secure in deployed HTTPS environments, SameSite=Lax for the proposed same-site topology, no Domain attribute, and Max-Age no longer than remaining session lifetime. Clear cookies with matching name/path/domain attributes. Local HTTP may use a local-only Secure=false override; production must reject insecure configuration.

Browser login/refresh responses return accessToken, tokenType=Bearer, expiresIn, and safe user DTO; the refresh value is only in Set-Cookie. Set Cache-Control: no-store on token/security responses. Accept API access only through Authorization, never the refresh cookie.

The existing unconditional CSRF disablement must change when cookie endpoints are introduced. Protect login/registration/refresh/logout browser POST routes with CSRF tokens and trusted-origin validation. Use Spring's SPA-compatible cookie/token support with independent portal CSRF cookie names and header names, or an equivalently tested design. The CSRF cookie is readable by JavaScript; the refresh cookie is not. CSRF bootstrap endpoints must not create an authenticated HTTP session.

Use separate security matcher/filter-chain behavior for cookie-based auth endpoints and Authorization-only business endpoints. Permit credentialed CORS only for explicit approved frontend origins, methods and headers; never wildcard origins with credentials. Test preflight. SameSite alone is not the CSRF defense. [Spring Security 6.5 CSRF guidance](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).

The frontend HTTP adapter keeps access state separately per portal, obtains CSRF state before credential POSTs, sends credentials:include only as needed, and refreshes once before retrying a failed request once. Serialize refresh across requests/tabs using a browser coordination strategy such as Web Locks + BroadcastChannel. Scope cookie/CSRF/cache clearing to the logged-out portal.

Do not automatically repeat non-idempotent business writes after ambiguous network failures. Guest-cart merge is a later cart endpoint with its own idempotency; do not make successful login depend on an unimplemented cart operation.

A genuinely cross-site frontend requires an explicit cookie/SameSite=None/Secure/CSRF review. Do not assume same-origin and same-site are interchangeable.

## 7. API contract

All routes are under /api/v1. The portal path variable is an allowlisted enum; reject unknown portals.

| Method | Path | Authentication / behavior |
| --- | --- | --- |
| GET | /auth/customer/csrf | Public; customer CSRF bootstrap; no-store |
| GET | /auth/backoffice/csrf | Public; backoffice CSRF bootstrap; no-store |
| POST | /auth/customer/register | Public with CSRF/origin checks; email/password; creates CUSTOMER, 201; does not silently log in |
| POST | /auth/customer/login | Public with CSRF/origin checks; customer credentials, 200 token response + cookie |
| POST | /auth/backoffice/login | Public with CSRF/origin checks; STAFF/ADMIN credentials only |
| POST | /auth/customer/refresh | Customer refresh cookie + CSRF; 200 token response + replacement cookie |
| POST | /auth/backoffice/refresh | Backoffice refresh cookie + CSRF; same rotation policy |
| POST | /auth/customer/logout | Customer cookie + CSRF; idempotent 204 + cookie clearing |
| POST | /auth/backoffice/logout | Backoffice cookie + CSRF; idempotent 204 + cookie clearing |
| POST | /auth/customer/logout-all | Valid CUSTOMER access JWT for that portal + CSRF; 204; revokes that user's devices |
| POST | /auth/backoffice/logout-all | Valid STAFF/ADMIN access JWT for that portal + CSRF; 204; revokes that user's devices |
| GET | /me | Any valid account/session; safe current user and portal DTO |
| GET | /management/me | BACKOFFICE session; STAFF or ADMIN; real operational identity endpoint |
| GET | /management/users | BACKOFFICE ADMIN; paginated safe users |
| PATCH | /management/users/{id}/role | BACKOFFICE ADMIN; allowlisted role; invalidate target sessions; audit |
| PATCH | /management/users/{id}/active | BACKOFFICE ADMIN; explicit boolean; invalidate on disable; audit |

Public endpoints never accept role/status fields. Prefer explicit role/active DTOs rather than generic user PATCH. Registration returns safe user identity and a login-next behavior matching the frontend; no verification email is claimed until email delivery exists.

Safe user response: id, email, username where present, fullName, phone/imageUrl where appropriate, role, isActive, emailVerified, createdAt/updatedAt as needed. Exclude password_hash, token_version, refresh hashes and internal audit metadata.

Use existing ProblemDetail format plus a stable machine-readable code if needed by the frontend. 400 for validation, 401 for invalid credentials/tokens, 403 for valid but insufficient permissions or failed CSRF, 409 for agreed identity/admin-invariant conflicts, 429 for throttling. Generic credential errors do not reveal account state. A duplicate-email 409 is a deliberate registration enumeration tradeoff; revisit with a generic email-based flow when email delivery is added.

Document no refresh token in the JSON schema, cookie/CSRF usage and public OpenAPI security overrides. Do not falsely declare Bearer required for login/refresh.

## 8. RBAC and ownership policy

| Action | Guest | CUSTOMER | STAFF | ADMIN |
| --- | --- | --- | --- | --- |
| Customer registration/login | Yes | Yes | Customer credentials only | Customer credentials only |
| Backoffice login | Valid STAFF/ADMIN credentials only | No | Yes | Yes |
| Read current identity | No | Own | Own | Own |
| Customer profile/address/cart/order | Public catalog only | Own resources | No customer portal identity | No customer portal identity |
| Operational identity | No | No | Yes | Yes |
| Operational order read/workflow (later) | No | No | Within agreed operations scope | Yes |
| Product/promotion administration (later) | No | No initially | No initially | Yes |
| List users / change role / disable | No | No | No | Yes, subject to admin invariants |
| Role assignment during registration | No | No | No | No public path |

Use request matchers as the outer gate and @PreAuthorize/service policy checks for privileged actions. Require correct session portal as well as role. Use ownership-constrained repository queries for customer objects; derive owner from the authenticated principal instead of trusting user_id in requests. A role match alone never grants access to another customer's order/address/cart.

The current Spring default scope/scp -> SCOPE_* mapping does not implement these roles. Add a dedicated authority converter backed by the current user/session context. [Spring Security 6.5 JWT resource-server documentation](https://docs.spring.io/spring-security/reference/6.5/servlet/oauth2/resource-server/jwt.html).

## 9. Proposed package layout

```text
com.nutee.backend
  auth/
    AuthController, AuthService
    RefreshTokenService, AuthSession, RefreshToken
    repositories/, dto/, audit/
  user/
    User, UserRepository, UserService
    UserManagementController, dto/
  security/
    SecurityConfiguration, JwtConfiguration
    JwtTokenService, CurrentUserAuthenticationConverter
    Role, Portal, CurrentUser
    SecurityErrorHandler, security properties
  config/
  common/
```

Adapt class count to implementation needs; do not create empty packages/interfaces. User owns user profile/role data; auth owns sessions/refresh history; security owns cross-cutting authentication/authorization wiring. Controllers use DTOs; services own transactions. Avoid a custom JWT parsing servlet filter when Spring Resource Server already handles bearer verification.

## 10. Ordered delivery milestones

### M0 - Baseline and schema contract

- Inspect live DB when Docker access is available; distinguish empty DB from a preexisting schema.
- Resolve the user/session/token field contract and document frontend DTO mapping.
- Verify Java 21, Maven Wrapper, dependency resolution and existing unit/integration checks.
- Retain current dependency line unless a verified compatibility/security issue requires an upgrade.
- Define local test credentials, same-site frontend origins and signing-key paths without committing secrets.

Done when: baseline checks are recorded and the first auth schema is concrete, including indexes, nullability, constraints and deletion/retention behavior.

### M1 - Persistence and project setup completion

- Add V1__create_auth_schema.sql for users/auth_sessions/refresh_tokens/auth audit state and any shared admin-invariant lock mechanism.
- Add matching JPA mappings/repositories and time abstraction through an injectable Clock.
- Keep ddl-auto=validate; no Hibernate schema generation.
- Specify idempotent local setup and one-time admin bootstrap. Use an operator-supplied password and explicit execution; never create a default production administrator on every startup.
- Update migration documentation from infrastructure-only status.

Done when: an empty PostgreSQL instance migrates; JPA validates; rerunning startup does not reapply migrations; case-insensitive email uniqueness and FKs hold.

### M2 - Credentials and internal JWT issuer

- Configure password hashing/provider; implement customer registration and both login routes.
- Add local/deployment signing-key configuration and internal JWT encoder/decoder.
- Implement claim validation and current user/session checks.
- Replace issuer-mode property assumptions/tests that currently require a remote JWK URI.
- Add safe user DTOs, /me, portal CSRF bootstrap and exact security allowlists.

Done when: a new CUSTOMER can register/login, receive access + cookie tokens, and call /me; wrong credentials/portal and invalid JWTs fail.

### M3 - Refresh, logout and browser security

- Implement atomic rotation, retained history and committed replay revocation.
- Add current-session logout and logout-all, portal cookies/CSRF, no-store and CORS.
- Add bounded abuse throttling and auth audit events.
- Add expired-family/history cleanup with retention that preserves replay evidence.

Done when: rotation succeeds once, old token reuse revokes the family, unrelated devices remain valid unless global logout is used, and browser security tests pass.

### M4 - RBAC and administrator operations

- Implement persisted-role authority mapping, management identity/user listing and explicit role/active operations.
- Add portal checks, default deny, last-admin protection and account/session invalidation.
- Exercise customer cross-account denial through meaningful ownership checks as domain APIs are introduced.
- Document the initial permissions matrix and future STAFF operations scope.

Done when: CUSTOMER cannot access management; STAFF can read operational identity but cannot administer users; ADMIN operations are audited and cannot remove the final active admin.

### M5 - Verification and operational handoff

- Run .\mvnw.cmd test and .\mvnw.cmd clean verify with Java 21 and accessible Docker.
- Update infrastructure IT assertions to expect the auth schema instead of zero entities/tables.
- Publish accurate OpenAPI examples, configuration and key rotation/retention instructions in the repository.
- Provide a repeatable register -> login -> /me -> refresh -> logout smoke sequence using a cookie jar and CSRF headers.
- Add CI unit + PostgreSQL integration checks without relying on a developer's local database.

Done when: all auth acceptance tests pass with no silently skipped database/concurrency tests, secrets are absent from responses/logs, and the documented local setup reproduces the behavior.

### M6 - Frontend auth adapter, separately scoped

- Replace demo credential/session storage with the API adapter while preserving separate portal state.
- Coordinate refresh across requests/tabs; map ProblemDetail errors; clear only appropriate private query caches.
- Test actual browser cookies/CSRF/CORS and logout independence.
- Remove demo passwords/credential storage from production UI.
- Keep remaining commerce mocks explicitly separated until their own backend endpoints exist.

The current writable workspace is backend. Frontend files were inspected read-only to ground this plan; this milestone requires a writable frontend task context.

## 11. Acceptance tests

| Category | Required scenarios |
| --- | --- |
| Migrations | Empty DB boot, JPA validation, repeated boot, unique canonical email race, FK/check constraints; adopt existing schemas explicitly |
| Registration | CUSTOMER-only assignment, invalid input, password never returned/stored plaintext, duplicate canonical email, no automatic verification |
| Login | Correct/incorrect passwords, unknown/disabled account, nullable unfinished profile, wrong portal, throttling, successful audit |
| JWT | Signed token round-trip, tampered signature, wrong issuer/audience, expired/future tokens, disallowed algorithm, unknown kid, absent/malformed sid/sub/ver/portal/type |
| Live revocation | Logged-out family rejected on subsequent request, disabled user denied, stale token_version denied, role change invalidates sessions, re-enable does not restore old sessions |
| Refresh | Successor replaces predecessor, expiry, absolute family lifetime, wrong portal, unknown digest, revoked family, consumed predecessor, refresh value never persisted |
| Concurrency | Parallel same-token requests cannot mint two usable successors; replay revocation remains committed; logout/refresh and disable/refresh races cannot leave usable sessions |
| Transaction failure | Token/session writes roll back together; no successful response/cookie on failed commit; replay rejection does not roll back revocation/audit |
| Logout | Idempotent current logout, logout with expired access, cookie clearing attributes, other portal/device unaffected, logout-all invalidates all user sessions |
| RBAC | Customer management denial; STAFF management identity allowed/user admin denied; ADMIN allowed; spoofed request role/user ID cannot elevate; unknown persisted role fails closed |
| Admin invariants | Cannot remove final active admin; concurrent demotions/disables cannot bypass rule; bootstrap never exposes a public privileged endpoint |
| Browser | Separate portal cookie paths/names; HttpOnly/Secure/SameSite, CSRF missing/invalid/cross-origin denial, correct CSRF success, preflight allow/deny, no JSESSIONID |
| Responses/logs | No password/hash/raw token in DTOs or logs; no-store; consistent ProblemDetail; public Swagger operations correctly described |
| Key lifecycle | Same key across restarts; overlapping public-key verification during rotation; unknown/retired key rejection |

Use PostgreSQL Testcontainers for persistence/locking races rather than H2 approximations. Inject a Clock for expiry tests. Use real signed JWTs for integration checks, retaining mocked decoder tests only where they test filter/error behavior. Browser checks belong in the frontend integration milestone.

## 12. Remaining policy decisions and limits

The plan can proceed with its stated defaults. Confirm or revise these before the affected deployment/feature:

- Production frontend/API origins and TLS termination determine final cookie/CORS settings.
- Whether email verification must gate checkout; no mail provider is configured in this auth slice.
- Whether login sessions need an inactivity timeout shorter than the seven-day absolute lifetime.
- Whether STAFF can edit catalog/promotions; current frontend policy keeps those ADMIN-only.
- Whether privileged production accounts require MFA before launch.
- Retention for auth audits, token history, IP and user-agent data.
- Whether product variants are separate purchasable SKUs or independent products in the frontend's long-term model; current mock and ERD differ.

Implementation order is M0 -> M1 -> M2 -> M3 -> M4 -> M5, followed by M6 when frontend integration is in scope. Review and migrate commerce tables incrementally after this auth foundation.
