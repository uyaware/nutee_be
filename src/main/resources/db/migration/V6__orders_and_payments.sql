-- Keep old orders readable; snapshots/idempotency are supplied for all newly placed orders.
ALTER TABLE orders
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN request_hash VARCHAR(64),
    ADD COLUMN recipient VARCHAR(80),
    ADD COLUMN phone VARCHAR(10),
    ADD COLUMN address_line TEXT,
    ADD COLUMN promotion_discount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN voucher_discount BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN inventory_released BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();
CREATE UNIQUE INDEX uk_orders_checkout_key ON orders (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
ALTER TABLE order_items
    ADD COLUMN product_name VARCHAR(255),
    ADD COLUMN sku_snapshot VARCHAR(16),
    ADD COLUMN image_url TEXT,
    ADD COLUMN configuration TEXT,
    ADD COLUMN original_price BIGINT;

CREATE SEQUENCE payment_gateway_order_code START WITH 1000000 MAXVALUE 9999999999 NO CYCLE;
CREATE TABLE payment_attempts (
    id BIGSERIAL PRIMARY KEY,
    order_id INTEGER NOT NULL UNIQUE REFERENCES orders(id),
    gateway_order_code BIGINT NOT NULL UNIQUE DEFAULT nextval('payment_gateway_order_code'),
    provider VARCHAR(20) NOT NULL DEFAULT 'payos',
    amount BIGINT NOT NULL CHECK (amount > 0),
    currency VARCHAR(3) NOT NULL DEFAULT 'VND',
    status VARCHAR(20) NOT NULL DEFAULT 'new',
    external_id VARCHAR(100) UNIQUE,
    checkout_url TEXT,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payment_attempts_reconcile ON payment_attempts(updated_at, id) WHERE status IN ('pending', 'error', 'creating', 'review');
CREATE TABLE payment_events (
    id VARCHAR(240) PRIMARY KEY,
    gateway_order_code BIGINT NOT NULL,
    external_id VARCHAR(100) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    result_code VARCHAR(20) NOT NULL,
    decision VARCHAR(40) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
