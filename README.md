# NUTEE Backend

Java 21, Spring Boot 3.5.16, Maven Wrapper 3.9.14, PostgreSQL 16+, Flyway,
Spring Data JPA, Spring Security, Jakarta Validation, Springdoc 2.10.0, Lombok,
JUnit 5, Mockito and PostgreSQL Testcontainers. Spring Boot manages dependency versions.

This modular monolith implements database migrations, authentication and public
catalog APIs for products, variants, brands and categories, plus authenticated
variant-based cart operations, account profile updates, delivery addresses, product promotions
and order vouchers with a server-side checkout quote. Checkout creates real orders with stock/voucher
reservations, configurable delivery fees, COD and payOS payments/webhooks through an adapter.
Fulfilment, carrier integration, refunds, after-sales mutations and catalog administration are not implemented.

Catalog setup and API reference: [docs/catalog-api.md](docs/catalog-api.md).
Cart endpoints and concurrency rules: [docs/cart-api.md](docs/cart-api.md).
Profile/address endpoints and ownership rules: [docs/account-api.md](docs/account-api.md).
Promotion/voucher rules and seed data: [docs/discounts-api.md](docs/discounts-api.md).
Order/payment API, adapter and merchant setup: [docs/payments-api.md](docs/payments-api.md).
Windows PostgreSQL and pgAdmin setup: [docs/postgresql-pgadmin.md](docs/postgresql-pgadmin.md).

## Run locally on Windows

With PostgreSQL installed on Windows, start the PostgreSQL service and set these
values in the existing ignored `.env` using your own database and credentials:

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/nutee
DB_USERNAME=postgres
DB_PASSWORD=your_postgresql_password
SERVER_PORT=8080
```

Keep the existing JWT, Google, and payOS values in `.env`. From this backend
directory run:

```powershell
.\scripts\run-local.ps1
```

The runner imports `.env`, selects JDK 21, checks whether the API port is busy,
and starts Spring Boot with the local profile. It does not start or require Docker.
Use `-JdkHome 'C:\path\to\jdk-21'` if the JDK is installed elsewhere. Ctrl+C
stops the backend. If port 8080 is occupied, use
`netstat -ano | findstr :8080` to identify the owning PID, then stop the relevant
application before retrying. The frontend Vite proxy currently targets port 8080.

Alternatively, `.\scripts\init-local.ps1` starts the optional Docker PostgreSQL
setup; it is not needed for PostgreSQL installed on Windows. Existing seed runner
scripts use Docker; see [catalog seed instructions](docs/catalog-api.md) before
loading sample data.

The fresh baseline consists of three Flyway migrations: `V1__init_schema.sql`,
`V2__add_constraints.sql`, and `V3__create_indexes.sql`. It includes the current
checkout, discount and payOS schema. V4 upgrades existing data in place: payment
state, QR and bank details live in `orders`; each webhook delivery is recorded in
`transaction_logs`, and `payment_events` is removed. Orders reference addresses without duplicate
contact columns or `inventory_released`.
This baseline requires an empty database. Existing deployments of the old V1–V7
chain require an explicit reset or a separately reviewed data migration; do not
repair or bypass Flyway validation. The local profile automatically seeds the
sample catalog after Flyway completes; set CATALOG_SEED_ENABLED=false to disable it.

If the database was dropped, create it explicitly with the existing Compose volume,
then start the API to apply the new migrations:

```powershell
docker compose exec -T postgres psql -U nutee -d postgres -v ON_ERROR_STOP=1 -c 'CREATE DATABASE nutee OWNER nutee;'
.\scripts\run-local.ps1
```

`init-local.ps1` reuses an existing volume; it does not recreate a manually dropped
database inside that volume.

When running with the `local` profile, the Java startup runner applies the catalog
seed automatically after Flyway. It creates 100 sample products, plus one 2,000 VND
payment test product with 100 units;
subsequent starts preserve existing sample prices and quantities. Set
`CATALOG_SEED_ENABLED=false` in `.env` and restart to turn it off. Other profiles
do not run this seed. Promotions and vouchers remain a separate opt-in seed.
See [Catalog API](docs/catalog-api.md) for details and limitations.

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

Backend xác thực bằng JWT; cookie HttpOnly chỉ dùng để lưu và tự gửi token.
Tên trong code và Swagger là `JwtAuthenticationFilter`, `JwtCookies`, `jwtAuth`.

### Tổ chức code

- `AuthController`: đăng ký, đăng nhập bằng mật khẩu, refresh và logout.
- `AuthService`: nghiệp vụ tài khoản, phiên JWT và liên kết danh tính.
- `HmacTokenService`: phát hành và kiểm tra access/refresh JWT; cấu hình nằm trong `JwtProperties`.
- `JwtAuthenticationFilter`: xác thực access JWT đọc từ cookie.
- `JwtCookies`: đặt và xóa cookie JWT, không xác minh token.
- `AuthResponse`: kết quả dùng chung; hai JWT có `@JsonIgnore`, chỉ dùng để đặt cookie.
- `SocialLoginProvider`: Strategy xác minh danh tính; hiện chỉ có `GoogleTokenVerifier`.
- `SocialLoginController`: chọn Strategy và gọi nghiệp vụ tài khoản dùng chung.

Khi được yêu cầu thêm provider, triển khai `SocialLoginProvider` và đăng ký bằng
`@Service`; không cần thêm factory hoặc sao chép nghiệp vụ đăng nhập. Hiện không có
implementation hay mock provider khác. `/auth/google` và trường `idToken` cũ vẫn
được giữ tương thích; `/auth/social/google` nhận cùng credential.

Quy tắc thay đổi code được ghi tại [AGENTS.md](AGENTS.md). Các class và method
viết tay trong `src/main/java` có Javadoc tiếng Việt mô tả công dụng.

The backend delivers accessToken and refreshToken only through HttpOnly Set-Cookie
headers. The browser sends both cookies automatically to /api/v1. JSON responses
contain only expiresIn, refreshExpiresIn (seconds) and the safe user DTO.

| Method / path | Body / purpose | Status |
| --- | --- | --- |
| GET /api/v1/auth/csrf | CSRF cookie + token/headerName for JavaScript | 200 |
| POST /api/v1/auth/register | email, password; optional username, fullName | 201 |
| POST /api/v1/auth/login | identifier (email or username), password, optional remember | 200 |
| POST /api/v1/auth/refresh | No body; reads refreshToken cookie and rotates both cookies | 200 |
| POST /api/v1/auth/logout | No body; revokes matching refresh and clears cookies | 204 |
| GET /api/v1/auth/me | Reads accessToken cookie | 200 |
| GET /api/v1/auth/google/nonce | Browser nonce + HttpOnly nonce cookie | 200 |
| POST /api/v1/auth/google?remember=false | idToken + nonce cookie | 200 |
| POST /api/v1/auth/google/link | idToken + nonce cookie + authenticated access cookie | 200 |
| POST /api/v1/auth/forgot-password | email; generic response when enabled | 200 |
| POST /api/v1/auth/reset-password | Single-use reset-link token, password | 204 |

Every state-changing request requires the CSRF header obtained from /auth/csrf.
The CSRF cookie is also HttpOnly; use the bootstrap JSON token rather than reading
document.cookie. CSRF tokens are rotated on login/register/Google login/logout.
Missing/invalid CSRF returns 403 with code=CSRF_INVALID. The frontend shared API
client reacquires CSRF and retries once; ordinary forbidden responses are not retried.
No HttpSession/JSESSIONID is created.

Example using the shared frontend client:

```typescript
import { api } from "./services";

await api.request("/auth/login", {
  method: "POST",
  authenticated: false,
  body: { identifier: "your-address@example.com", password: "your-password", remember: true },
});
const user = await api.request("/auth/me");
await api.request("/auth/refresh", { method: "POST", authenticated: false });
await api.request("/auth/logout", { method: "POST", authenticated: false });
```

For raw fetch, set credentials:include on every call, bootstrap CSRF before POST,
and send the returned token using its headerName. Do not copy JWTs into a request
body or Authorization header. Access-cookie authentication skips public auth routes,
so an expired access cookie cannot block login, refresh or logout.

Username is generated when omitted, or accepts 3-50 ASCII letters/digits/underscores.
Email/username identities are case-insensitive. Passwords require 12+ characters
and at most 72 UTF-8 bytes, and use BCrypt strength 12. Registration assigns CUSTOMER.
Disabled accounts cannot login/refresh/fetch their profile. Errors use ProblemDetail:
400 validation, 401 invalid session, 403 CSRF/permissions, 409 identity conflict.

## Cookies and HMAC tokens

| Variable | Purpose / default |
| --- | --- |
| JWT_ACCESS_SECRET | Mandatory Base64 encoding of 32+ random bytes |
| JWT_REFRESH_SECRET | Mandatory different Base64 secret of 32+ random bytes |
| ACCESS_TOKEN_TTL | 15m |
| REFRESH_TOKEN_TTL | 7d absolute refresh lifetime |
| AUTH_SECURE_COOKIE | true; local profile defaults to false for HTTP |
| AUTH_COOKIE_SAME_SITE | Lax; None requires Secure/HTTPS |

Auth cookies are host-only and use Path=/api/v1, HttpOnly and configured SameSite.
Without remember=true they are browser-session cookies. Remembered access cookies
have Max-Age=expiresIn; refresh/remember cookies have Max-Age=refreshExpiresIn.
Cookies are shared by the browser's tabs and both portals; a new login replaces the
current browser account. A logout clears both portal views. JavaScript never stores
JWTs in localStorage/sessionStorage or reads them from cookies.

NUTEE JWTs use separate HS256 keys and omit iss/aud. Do not share these keys with
other services. Verification checks signature, timestamps, sub/jti and token_type.
Each user stores one current signed refresh JWT in users.refresh_token. Login
replaces it; refresh locks the user, verifies equality and rotates both JWTs while
preserving the original absolute refresh deadline. Access JWTs do not authenticate
refresh requests and refresh JWTs do not authenticate protected API calls.

Logout clears the matching stored refresh token and browser cookies. Issued access
JWTs remain cryptographically valid until expiry; browser JavaScript cannot retrieve
them. Endpoints requiring live account state must check it explicitly (/auth/me does).
See [token design](docs/token-design.md).

## Enable Google sign-in

Hướng dẫn tiếng Việt: [cấu hình Google Auth và liên kết tài khoản](docs/google-auth.md).

Google remains disabled until GOOGLE_AUTH_ENABLED=true and GOOGLE_CLIENT_ID is set.
Use a Google OAuth Web Client ID and configure the exact frontend JavaScript origins.
Set that same Client ID as VITE_GOOGLE_CLIENT_ID in frontend/.env.local and restart Vite.
No Google API key or client secret is needed for this ID-token flow.

The frontend loads Google Identity Services, obtains /auth/google/nonce, initializes
Google with that nonce, then submits the credential to /auth/google through the shared
CSRF/cookie API client. The backend verifies Google's RSA signature, issuer, audience,
timestamps, email_verified, stable sub and browser nonce. Existing-email accounts
require explicit authenticated linking; they are not automatically merged.

Prefer same-origin hosting through an /api/v1 reverse proxy. Direct backend calls
need explicit CORS_ALLOWED_ORIGINS and credentials:include; production uses HTTPS.
Google nonce cookies use SameSite=Lax, so the Google browser flow assumes same-site
frontend/API hosting. Google setup: [create a client ID](https://developers.google.com/identity/gsi/web/guides/get-google-api-clientid),
[verify ID tokens](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token).

## Schema, architecture and operations

### Password recovery and frontend connection

The frontend uses `/api/v1/auth` for registration, login, Google login, current
user, refresh and logout. Vite proxies `/api/v1` to localhost:8080; production needs
the same reverse proxy or an explicit `VITE_API_BASE_URL` plus backend CORS origins.
Frontend reads account details and Google-link status from `/account/profile` and
updates profile/address records through the authenticated account APIs. `/auth/me`
still supplies the login session. Browser-storage services
and seed data have been removed. Existing UI, styles and client-side interactions
are preserved; screens without backend APIs use empty data and cannot simulate saves.

The baseline retains `users.password_reset_hash` and `password_reset_expires_at`.
Reset links contain a random 256-bit token, stored only as SHA-256,
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

The baseline creates all 23 tables from the updated ERD. Schema creation,
constraints, and explicit indexes are separated into V1/V2/V3. Existing
refresh-token/password-reset fields remain for authentication compatibility.
See [schema decisions](docs/schema-decisions.md) for typing corrections and the
reset boundary. Future applied migrations must remain immutable; add new versions.

The base package is com.nutee.backend. `auth` owns controllers/DTOs/services,
provider links and refresh-token flows; `user` owns the user entity/repository;
`security` owns token verification, BCrypt and CORS; `common` owns shared error
and pagination DTOs; `config` owns OpenAPI. Transaction boundaries are in services,
dependencies use constructor injection, and persistence entities stay out of APIs.
Domain tables without implemented behavior do not receive empty Java classes.

Product, brand and category code is organized by feature. `product` contains
search/response assembly and the `variant`, `image`, `review` packages;
variant specifications live in `product.variant.specification`. Each resource
owns its query/response types. Public catalog URLs and payloads stay unchanged;
see [Catalog API](docs/catalog-api.md) for the package layout.

Business persistence is implemented with Spring Data JPA, including product,
brand, category, variants, images, specifications and review/purchase references.
Repositories prefer built-in methods and derived queries with Sort/EntityGraph.
Specifications handle dynamic filtering; explicit JPQL is reserved for facets,
grouped review counts and latest-review projections. Related rows are fetched
in batches after database pagination. Application code does not use JdbcTemplate
or direct JDBC. Flyway/seed/test SQL remains separate, and Hibernate only validates
the schema. `AGENTS.md` makes this JPA approach mandatory for subsequent changes.

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

Catalog integration tests use isolated transaction-scoped fixtures, not a
production seed script. Schema integration checks cover the new order/variant
relationships and verify that a fresh database starts empty.

Executable JAR: `target/backend-0.0.1-SNAPSHOT.jar`. With environment variables set,
run `java -jar target/backend-0.0.1-SNAPSHOT.jar` for deployment.
