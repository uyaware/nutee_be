ALTER TABLE users ADD COLUMN password_reset_hash VARCHAR(64);
ALTER TABLE users ADD COLUMN password_reset_expires_at TIMESTAMPTZ;
CREATE UNIQUE INDEX users_password_reset_hash_key ON users(password_reset_hash)
    WHERE password_reset_hash IS NOT NULL;
