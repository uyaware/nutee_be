-- Delivery contacts come from users; orders reference the existing user_address_id.
-- Remove obsolete copies without changing user profiles, addresses or order references.
ALTER TABLE user_addresses
    DROP COLUMN recipient,
    DROP COLUMN phone;

ALTER TABLE orders
    DROP COLUMN recipient,
    DROP COLUMN phone,
    DROP COLUMN address_line;
