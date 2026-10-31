-- Current schema, assembled from the fresh Flyway baseline to avoid drift.
-- Review/test bootstrap only: use Flyway to provision or upgrade application databases.
-- Run against an EMPTY database/schema:
-- psql --single-transaction --file=docs/schema.sql <connection options>
\set ON_ERROR_STOP on
\ir ../src/main/resources/db/migration/V1__init_schema.sql
\ir ../src/main/resources/db/migration/V2__add_constraints.sql
\ir ../src/main/resources/db/migration/V3__create_indexes.sql
\ir ../src/main/resources/db/migration/V4__move_payment_details_to_orders.sql
