# Flyway migrations

The user explicitly requested a fresh baseline from the updated ERD and dropped
the previous local database and catalog seed data.

1. `V1__init_schema.sql`: all 23 tables, columns, SERIAL sequences and defaults.
2. `V2__add_constraints.sql`: PK, FK, UNIQUE, CHECK and explicit NOT NULL constraints.
3. `V3__create_indexes.sql`: explicit lookup, expression and partial unique indexes.
4. `V4__address_contacts.sql`: adds recipient/phone to delivery addresses and backfills
   from the owner profile where available; preserves existing address data.

Authentication refresh-token/password-reset fields remain in V1 for compatibility.
No seed INSERTs are included. Hibernate only validates.

The V1–V3 reset baseline requires an empty database. V4 upgrades databases already
using that baseline. Existing databases using the pre-reset chain
need a deliberate reset or a separate data migration; do not repair/baseline away
checksum mismatches. Future changes must be new immutable versions.
See `docs/schema-decisions.md` for the source ERD and typing/relationship decisions.
