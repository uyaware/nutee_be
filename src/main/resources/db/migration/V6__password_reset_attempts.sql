ALTER TABLE users ADD COLUMN password_reset_attempts integer NOT NULL DEFAULT 0;
ALTER TABLE users ADD CONSTRAINT ck_password_reset_attempts CHECK (password_reset_attempts BETWEEN 0 AND 5);
-- Các liên kết khôi phục cũ không thuộc luồng OTP mới.
UPDATE users SET password_reset_hash = NULL, password_reset_expires_at = NULL;
