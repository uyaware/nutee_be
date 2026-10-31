-- Contacts belong to each delivery address, independently of the account profile.
-- Existing addresses remain valid even when their owner's contact is incomplete.
ALTER TABLE user_addresses
    ADD COLUMN recipient VARCHAR(80),
    ADD COLUMN phone VARCHAR(10);

UPDATE user_addresses a SET
    recipient = left(coalesce(nullif(trim(u.full_name), ''), u.username), 80),
    phone = u.phone
FROM users u WHERE u.id = a.user_id;
