ALTER TABLE users
    ADD COLUMN email_verification_hash VARCHAR(64),
    ADD COLUMN email_verification_expires_at TIMESTAMPTZ,
    ADD COLUMN email_verification_attempts INTEGER NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_email_verification_attempts CHECK (email_verification_attempts BETWEEN 0 AND 5);
