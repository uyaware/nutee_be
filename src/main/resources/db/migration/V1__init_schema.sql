-- Fresh baseline for NUTEE; initialize only an empty database.
-- Table/column definitions and defaults only. Constraints are in V2; indexes in V3.
-- SERIAL supplies its PostgreSQL sequence/default and implicit column nullability.

CREATE TABLE users (
    id UUID,
    username VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),
    refresh_token TEXT,
    password_reset_hash VARCHAR(64),
    password_reset_expires_at TIMESTAMPTZ,
    full_name VARCHAR(100),
    phone VARCHAR(10),
    email_verified BOOLEAN DEFAULT false,
    image_url TEXT,
    role VARCHAR(255) DEFAULT 'CUSTOMER',
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE user_addresses (
    id SERIAL,
    user_id UUID,
    province_city VARCHAR(255),
    ward_commune VARCHAR(255),
    street VARCHAR(255),
    is_default BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE auth_providers (
    id SERIAL,
    user_id UUID,
    provider VARCHAR(255),
    provider_user_id VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE products (
    id SERIAL,
    promotion_id UUID,
    brand_id INT,
    default_variant_id INT,
    name VARCHAR(255),
    description TEXT,
    avg_rating NUMERIC(2,1),
    sold_count INT,
    is_published BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE product_images (
    id SERIAL,
    product_id INT,
    image_url TEXT,
    is_primary BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE variant_specifications (
    id SERIAL,
    variant_id INT,
    specification_name VARCHAR(255),
    specification_value TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE brands (
    id SERIAL,
    name VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE categories (
    id SERIAL,
    parent INT,
    name VARCHAR(255),
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE product_categories (
    product_id INT,
    cat_id INT
);

CREATE TABLE cart_items (
    id SERIAL,
    user_id UUID,
    variant_id INT,
    sku VARCHAR(16),
    quantity INT DEFAULT 1,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE reviews (
    id SERIAL,
    order_item_id INT
);

CREATE TABLE orders (
    id SERIAL,
    order_code VARCHAR(10),
    user_id UUID,
    voucher_id UUID,
    user_address_id INTEGER,
    total_amount NUMERIC(18, 2),
    sub_total NUMERIC(18, 2),
    shipping_fee INT,
    discount_amount NUMERIC(18, 2),
    status VARCHAR(255),
    note TEXT,
    payment_method VARCHAR(255),
    payment_amount NUMERIC(18, 2),
    payment_status VARCHAR(255),
    payment_description TEXT,
    payment_expired_at TIMESTAMPTZ,
    idempotency_key UUID,
    request_hash VARCHAR(64),
    promotion_discount BIGINT NOT NULL DEFAULT 0,
    voucher_discount BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE order_histories (
    id SERIAL,
    order_id INT,
    updated_by UUID,
    order_status VARCHAR(255),
    occurred_at TIMESTAMPTZ
);

CREATE TABLE vouchers (
    id UUID,
    code VARCHAR(20),
    applied_for VARCHAR(255),
    type VARCHAR(255),
    value INT,
    is_active BOOLEAN,
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    quantity INT,
    max_uses_per_user INT,
    min_order BIGINT NOT NULL DEFAULT 0,
    max_discount BIGINT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE user_vouchers (
    user_id UUID,
    voucher_id UUID
);

CREATE TABLE promotions (
    id UUID,
    code VARCHAR(20),
    applied_for VARCHAR(255),
    type VARCHAR(255),
    value INT,
    is_active BOOLEAN,
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE order_items (
    id SERIAL,
    order_id INT,
    variant_id INT,
    quantity INT DEFAULT 1,
    calculated_price NUMERIC(18, 2),
    warranty_expired_at TIMESTAMPTZ,
    product_name VARCHAR(255),
    sku_snapshot VARCHAR(16),
    image_url TEXT,
    configuration TEXT,
    original_price BIGINT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE aftersale_requests (
    id SERIAL,
    order_item_id INT,
    type VARCHAR(255),
    reason TEXT,
    description TEXT,
    images TEXT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE aftersale_request_histories (
    id SERIAL,
    request_id INT,
    user_id UUID,
    request_status VARCHAR(255),
    occurred_at TIMESTAMPTZ
);

CREATE SEQUENCE payment_gateway_order_code START WITH 1000000 MAXVALUE 9999999999 NO CYCLE;

CREATE TABLE transaction_logs (
    id SERIAL,
    order_id INT,
    gateway_order_code BIGINT DEFAULT nextval('payment_gateway_order_code'),
    provider VARCHAR(20) DEFAULT 'payos',
    amount BIGINT,
    currency VARCHAR(3) DEFAULT 'VND',
    status VARCHAR(255) DEFAULT 'new',
    external_id VARCHAR(100),
    checkout_url TEXT,
    lease_token UUID,
    lease_until TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    raw_body JSONB,
    request_header TEXT,
    error TEXT,
    attempt_count INT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE payment_events (
    id VARCHAR(240),
    gateway_order_code BIGINT,
    external_id VARCHAR(100),
    amount BIGINT,
    currency VARCHAR(3),
    result_code VARCHAR(20),
    decision VARCHAR(40),
    received_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE product_variants (
    id SERIAL,
    product_id INT,
    image_id INT,
    sku VARCHAR(16),
    price INT,
    quantity INT,
    created_at TIMESTAMPTZ DEFAULT now(),
    updated_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE review_histories (
    id SERIAL,
    user_id UUID,
    review_id INT,
    rating INT,
    comment TEXT,
    created_at TIMESTAMPTZ DEFAULT now()
);

CREATE TABLE event_logs (
    id UUID,
    user_id UUID,
    session_id UUID,
    product_id INT,
    event_type VARCHAR(100),
    rating INT,
    quantity INT,
    occurred_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ
);
