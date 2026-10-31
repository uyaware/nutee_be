# Flyway migrations

The user explicitly requested a fresh baseline from the updated ERD and dropped
the previous local database and catalog seed data.

1. `V1__init_schema.sql`: all 23 tables, columns, SERIAL sequences and defaults.
2. `V2__add_constraints.sql`: PK, FK, UNIQUE, CHECK and explicit NOT NULL constraints.
3. `V3__create_indexes.sql`: explicit lookup, expression and partial unique indexes.
4. `V4__address_contacts.sql`: adds recipient/phone to delivery addresses and backfills
   from the owner profile where available; preserves existing address data.
5. `V5__discount_rules.sql`: voucher order minimum/cap and unique normalized discount codes.
6. `V6__orders_and_payments.sql`: order snapshots/idempotency, inventory release tracking,
   stable payOS attempt codes and deduplicated payment-event audit; preserves existing orders.
7. `V7__remove_duplicate_delivery_contacts.sql`: removes recipient/phone from delivery addresses
   and recipient/phone/address_line from orders. Contacts come from users and orders retain
   the existing user_address_id. V4/V6 remain unchanged; payOS setup stays in application.yml.

Authentication refresh-token/password-reset fields remain in V1 for compatibility.
No seed INSERTs are included. Hibernate only validates.

The V1–V3 reset baseline requires an empty database. V4 upgrades databases already
using that baseline. Existing databases using the pre-reset chain
need a deliberate reset or a separate data migration; do not repair/baseline away
checksum mismatches. V7 intentionally removes the obsolete contact/address copies, retaining
user profiles, address rows and order-address references. Future changes must be new immutable versions.
See `docs/schema-decisions.md` for the source ERD and typing/relationship decisions.
