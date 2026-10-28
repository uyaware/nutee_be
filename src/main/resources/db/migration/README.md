# Flyway migrations

V1 implements all 24 tables from the supplied ERD. V2 adds refresh-session state.
See docs/schema-decisions.md for naming, type fixes and preserved business limits.
Never edit applied migrations; add another version. Hibernate only validates.
