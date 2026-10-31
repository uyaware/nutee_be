# Flyway migrations

The user explicitly requested a fresh three-version baseline and a reset of the
local NUTEE database. This chain is for an empty database, not an in-place upgrade.

1. `V1__init_schema.sql`: 24 domain tables, columns, sequences and defaults.
2. `V2__add_constraints.sql`: PK, FK, UNIQUE, CHECK and explicit NOT NULL constraints.
3. `V3__create_indexes.sql`: explicit lookup, expression and partial unique indexes.
Payment session state is stored in `transaction_logs`, and `payment_events` stores
deduplicated callbacks. User contact details remain in `users`; orders reference an
address via `user_address_id` and do not have an `inventory_released` column.

Authentication refresh-token/password-reset fields remain in V1 for compatibility.
No seed INSERTs are included. Hibernate only validates.

Existing databases using the previous V1–V7 chain need a deliberate reset or a
separately reviewed data migration; do not repair/baseline away checksum mismatches.
Future changes must use new immutable versions after consulting the user.
See `docs/schema-decisions.md` for the source ERD and typing/relationship decisions.
