# Flyway migrations

The user explicitly requested a fresh three-version baseline and a reset of the
local NUTEE database. This chain is for an empty database, not an in-place upgrade.

1. `V1__init_schema.sql`: 24 domain tables, columns, sequences and defaults.
2. `V2__add_constraints.sql`: PK, FK, UNIQUE, CHECK and explicit NOT NULL constraints.
3. `V3__create_indexes.sql`: explicit lookup, expression and partial unique indexes.
4. `V4__move_payment_details_to_orders.sql`: moves payment/QR details into `orders`,
   transfers old event records to `transaction_logs`, and removes `payment_events`.
5. `V5__allow_system_order_history.sql`: allows a null actor on system-generated
   order history entries, without changing existing records.
The current schema has 23 domain tables. `orders` is the only payment state;
`transaction_logs` records each verified webhook delivery, including duplicates.
Legacy session rows are retained with `status=legacy_session`; old event payloads
and headers remain NULL because the previous implementation never captured them.
User contact details remain in `users`; orders reference an
address via `user_address_id` and do not have an `inventory_released` column.

Authentication refresh-token/password-reset fields remain in V1 for compatibility.
No seed INSERTs are included. Hibernate only validates.

`event_logs` follows the shared Event payload: `event_id` is the caller-supplied
UUID primary key, `timestamp` is the event time (`TIMESTAMPTZ`), and
`received_at` is the database ingestion time. String identifiers are intentionally
not foreign keys, so anonymous visitors and simulator IDs remain valid.
Optional payload fields accept NULL; required fields and non-null defaults are
enforced in V2. `synthetic=true` matches the supplied model; real website events
must explicitly send false. `consent=false` records a value, not enforcement of
permission to collect or forward events. The event-name allowlist currently uses
the eight names in V2; keep it synchronized with the producer's EventName enum.

The Event payload alignment edits V1–V3 for fresh database initialization.
Databases that already applied these versions need a separately reviewed upgrade;
do not run Flyway repair or reset an existing database to bypass checksums.

Existing databases using the previous V1–V7 chain need a deliberate reset or a
separately reviewed data migration; do not repair/baseline away checksum mismatches.
Future changes must use new immutable versions after consulting the user.
See `docs/schema-decisions.md` for the source ERD and typing/relationship decisions.
