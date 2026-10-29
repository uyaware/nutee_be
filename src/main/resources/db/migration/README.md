# Flyway migrations

V1 implements all 24 tables from the supplied ERD, including the nullable TEXT
column users.refresh_token. There is no separate refresh-session table.
V2__add_password_reset.sql adds the reset-token hash and expiry fields to users.
See docs/schema-decisions.md for naming, type fixes and preserved business limits.
Hibernate only validates.

The user requested removing the earlier V2__create_refresh_sessions.sql and folding the single refresh-token
field into V1. Databases that already applied the previous V1/V2 require explicit
schema and Flyway-history reconciliation before this version can start. Do not
erase existing data or change migration history automatically.
