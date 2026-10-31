-- Payment state belongs to the order; transaction_logs records webhook deliveries.
ALTER TABLE orders
    ADD COLUMN gateway_order_code BIGINT,
    ADD COLUMN checkout_url TEXT,
    ADD COLUMN qr_code TEXT,
    ADD COLUMN bank_bin VARCHAR(20),
    ADD COLUMN account_number VARCHAR(100),
    ADD COLUMN account_name VARCHAR(255),
    ADD COLUMN transfer_content VARCHAR(255),
    ADD COLUMN payment_attempt_count INT NOT NULL DEFAULT 0;

UPDATE orders o
SET gateway_order_code = t.gateway_order_code,
    checkout_url = t.checkout_url,
    qr_code = t.raw_body ->> 'qrCode',
    bank_bin = t.raw_body ->> 'bin',
    account_number = t.raw_body ->> 'accountNumber',
    account_name = t.raw_body ->> 'accountName',
    transfer_content = t.raw_body ->> 'description',
    payment_attempt_count = GREATEST(COALESCE(t.attempt_count, 0), 1),
    payment_status = CASE WHEN o.payment_status = 'paid' THEN 'paid' ELSE t.status END
FROM transaction_logs t
WHERE t.order_id = o.id;

-- Do not invent the transfer description of an already-created legacy QR.
ALTER TABLE orders ALTER COLUMN gateway_order_code SET DEFAULT nextval('payment_gateway_order_code');
UPDATE orders SET gateway_order_code = nextval('payment_gateway_order_code') WHERE gateway_order_code IS NULL;
ALTER TABLE orders
    ALTER COLUMN gateway_order_code SET NOT NULL,
    ADD CONSTRAINT uk_orders_gateway_code UNIQUE (gateway_order_code),
    ADD CONSTRAINT ck_orders_payment_attempt_count CHECK (payment_attempt_count >= 0);

DROP INDEX idx_transaction_logs_reconcile;
ALTER TABLE transaction_logs
    DROP CONSTRAINT uk_transaction_logs_order,
    DROP CONSTRAINT uk_transaction_logs_gateway_code,
    DROP CONSTRAINT uk_transaction_logs_external_id,
    DROP CONSTRAINT ck_transaction_logs_attempt_count,
    DROP CONSTRAINT ck_transaction_logs_amount,
    ALTER COLUMN order_id DROP NOT NULL,
    ALTER COLUMN gateway_order_code DROP DEFAULT,
    ADD COLUMN reference VARCHAR(240),
    ADD COLUMN result_code VARCHAR(20),
    DROP COLUMN external_id,
    DROP COLUMN lease_token,
    DROP COLUMN lease_until,
    DROP COLUMN checkout_url,
    DROP COLUMN expires_at,
    DROP COLUMN attempt_count;

-- Keep legacy session records for audit without presenting their QR data as a webhook.
UPDATE transaction_logs
SET status = 'legacy_session', raw_body = NULL, request_header = NULL;

-- Old events never stored payloads/headers; leave those fields NULL rather than fabricate them.
INSERT INTO transaction_logs (order_id, gateway_order_code, provider, amount, currency,
    reference, result_code, status, created_at, updated_at)
SELECT o.id, e.gateway_order_code, 'payos', e.amount, e.currency,
    e.id, e.result_code, e.decision, e.received_at, e.received_at
FROM payment_events e
LEFT JOIN orders o ON o.gateway_order_code = e.gateway_order_code;

DROP TABLE payment_events;
ALTER TABLE transaction_logs ADD CONSTRAINT ck_transaction_logs_amount CHECK (amount >= 0);
CREATE INDEX idx_transaction_logs_order ON transaction_logs (order_id, created_at);
CREATE INDEX idx_transaction_logs_reference ON transaction_logs (provider, reference);
CREATE INDEX idx_orders_payment_reconcile ON orders (updated_at, id)
    WHERE payment_method = 'qr' AND payment_attempt_count > 0
        AND payment_status IN ('pending', 'error', 'creating', 'review');
