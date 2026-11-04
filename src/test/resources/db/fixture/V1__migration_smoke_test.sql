-- Test infrastructure only: this runs in an isolated schema, never in production.
CREATE TABLE migration_probe (
    id INTEGER PRIMARY KEY,
    description VARCHAR(100) NOT NULL
);

INSERT INTO migration_probe (id, description) VALUES (1, 'Flyway migration executed');
