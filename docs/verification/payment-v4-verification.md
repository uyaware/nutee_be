# Payment V4 verification

Verified on 2026-10-31 (Asia/Saigon).

- Local `nutee` database: Flyway V1-V4 applied successfully; all 3 existing orders retained.
- Backup created before migration: `.tools/nutee-before-payment-v4.dump` (ignored, local only).
- Backend starts with Hibernate schema validation; `/api/v1/actuator/health` returns `UP`.
- Full `mvn verify`: 39 unit/web tests passed; 74 integration tests passed;
  1 opt-in live payOS test skipped. Integration tests use isolated PostgreSQL 16 containers.
- Migration coverage includes a V3 database with an existing payment session and event,
  preserving the payment code, QR, known transfer description and old audit rows.
- Payment coverage includes repeat/concurrent webhooks, preserved request JSON and safe headers,
  amount/currency/link mismatches, callbacks before QR creation completes, stale create responses,
  cancellation, late payments and provider reconciliation.
- Frontend production build succeeded; all 6 payment Playwright tests passed.
  These include bank details and order-code transfer content after checkout and page reload.
- ESLint passed for the changed TypeScript files; formatting checked with tab width 4.
- Physical ERD: 23 domain tables, 196 columns, 30 foreign keys; no table overlaps.
  Column/default/nullability/constraint/index comparison against local schema found 0 differences.
  See `payment-v4-schema-comparison.json` and `payment-v4-drawio.json`.
- Drive file updated in place and downloaded again; SHA-256 matches the local draw.io:
  `12d7a5436330caa18805f860a945c08424ef9844b4e8fb330a096bd1f9403e2f`.
  The conceptual EERD page is unchanged.

Legacy payment sessions only stored a QR, not bank account/name or the description.
Those missing fields remain NULL; new payOS create responses supply the complete details.
Legacy session rows are marked `legacy_session`. Old event payloads/headers were never
captured, so the migration does not fabricate webhook data for them.
No real money transfer or live merchant API request was performed for this change.
