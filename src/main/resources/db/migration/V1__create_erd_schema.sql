-- Source: supplied NUTEE ERD; see docs/schema-decisions.md for explicit corrections.

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password VARCHAR(255),
    refresh_token TEXT,
    full_name VARCHAR(100),
    phone VARCHAR(10),
    email_verified BOOLEAN NOT NULL DEFAULT false,
    image_url TEXT,
    role VARCHAR(255) NOT NULL DEFAULT 'CUSTOMER',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_addresses (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    province_city VARCHAR(255),
    ward_commune VARCHAR(255),
    street VARCHAR(255),
    is_default BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auth_providers (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    provider VARCHAR(255) NOT NULL,
    provider_user_id VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (provider, provider_user_id),
    UNIQUE (user_id, provider)
);

CREATE TABLE promotions (
    id UUID PRIMARY KEY,
    code VARCHAR(20),
    applied_for VARCHAR(255),
    type VARCHAR(255),
    value INT,
    is_active BOOLEAN,
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE vouchers (
    id UUID PRIMARY KEY,
    code VARCHAR(20),
    applied_for VARCHAR(255),
    type VARCHAR(255),
    value INT,
    is_active BOOLEAN,
    start_date TIMESTAMPTZ,
    end_date TIMESTAMPTZ,
    quantity INT,
    max_uses_per_user INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE brands (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE categories (
    id SERIAL PRIMARY KEY,
    parent INT REFERENCES categories(id),
    name VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE products (
    id SERIAL PRIMARY KEY,
    promotion_id UUID REFERENCES promotions(id),
    name VARCHAR(255),
    description TEXT,
    avg_rating NUMERIC(2,1),
    sold_count INT,
    is_published BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product_variants (
    id SERIAL PRIMARY KEY,
    product_id INT NOT NULL REFERENCES products(id),
    sku VARCHAR(16) UNIQUE NOT NULL,
    price INT,
    quantity INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (product_id, sku)
);

CREATE TABLE product_images (
    id SERIAL PRIMARY KEY,
    product_id INT NOT NULL REFERENCES products(id),
    image_url TEXT,
    is_primary BOOLEAN,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product_specifications (
    id SERIAL PRIMARY KEY,
    product_id INT NOT NULL REFERENCES products(id),
    specification_name VARCHAR(255),
    specification_value TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE product_brands (
    product_id INT NOT NULL REFERENCES products(id),
    brand_id INT NOT NULL REFERENCES brands(id),
    PRIMARY KEY (product_id, brand_id)
);

CREATE TABLE product_categories (
    product_id INT NOT NULL REFERENCES products(id),
    cat_id INT NOT NULL REFERENCES categories(id),
    PRIMARY KEY (product_id, cat_id)
);

CREATE TABLE cart_items (
    user_id UUID NOT NULL REFERENCES users(id),
    product_id INT NOT NULL,
    sku VARCHAR(16) NOT NULL,
    quantity INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, product_id),
    FOREIGN KEY (product_id, sku) REFERENCES product_variants(product_id, sku)
);

CREATE TABLE reviews (
    id SERIAL PRIMARY KEY,
    product_id INT NOT NULL,
    sku VARCHAR(16) NOT NULL,
    FOREIGN KEY (product_id, sku) REFERENCES product_variants(product_id, sku)
);

CREATE TABLE orders (
    order_code UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    voucher_id UUID REFERENCES vouchers(id),
    user_address_id INTEGER REFERENCES user_addresses(id),
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
    payment_expired_at TIMESTAMPTZ
);

CREATE TABLE order_histories (
    id SERIAL PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(order_code),
    updated_by UUID NOT NULL REFERENCES users(id),
    order_status VARCHAR(255),
    occurred_at TIMESTAMPTZ
);

CREATE TABLE order_items (
    order_code UUID NOT NULL REFERENCES orders(order_code),
    product_id INT NOT NULL,
    sku VARCHAR(16) NOT NULL,
    quantity INT,
    calculated_price NUMERIC(18, 2),
    warranty_expired_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (order_code, product_id),
    FOREIGN KEY (product_id, sku) REFERENCES product_variants(product_id, sku)
);

CREATE TABLE user_vouchers (
    user_id UUID NOT NULL REFERENCES users(id),
    voucher_id UUID NOT NULL REFERENCES vouchers(id),
    PRIMARY KEY (user_id, voucher_id)
);

CREATE TABLE aftersale_requests (
    id SERIAL PRIMARY KEY,
    product_id INT NOT NULL,
    sku VARCHAR(16) NOT NULL,
    type VARCHAR(255),
    reason TEXT,
    description TEXT,
    images TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (product_id, sku) REFERENCES product_variants(product_id, sku)
);

CREATE TABLE aftersale_request_histories (
    id SERIAL PRIMARY KEY,
    request_id INT NOT NULL REFERENCES aftersale_requests(id),
    user_id UUID NOT NULL REFERENCES users(id),
    request_status VARCHAR(255),
    occurred_at TIMESTAMPTZ
);

CREATE TABLE transaction_logs (
    id SERIAL PRIMARY KEY,
    order_code UUID NOT NULL REFERENCES orders(order_code),
    status VARCHAR(255),
    raw_body JSONB,
    request_header TEXT,
    error TEXT,
    attempt_count INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE review_histories (
    id SERIAL PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    review_id INT NOT NULL REFERENCES reviews(id),
    rating INT,
    comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE event_logs (
    id UUID PRIMARY KEY,
    user_id UUID,
    session_id UUID,
    product_id INT,
    event_type VARCHAR(100),
    rating INT,
    quantity INT,
    occurred_at TIMESTAMPTZ,
    received_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_users_username_ci ON users (lower(username));
CREATE UNIQUE INDEX uk_users_email_ci ON users (lower(email));

CREATE INDEX idx_user_addresses_user_id ON user_addresses (user_id);
CREATE INDEX idx_auth_providers_user_id ON auth_providers (user_id);
CREATE INDEX idx_categories_parent ON categories (parent);
CREATE INDEX idx_products_promotion_id ON products (promotion_id);
CREATE INDEX idx_product_variants_product_id ON product_variants (product_id);
CREATE INDEX idx_product_images_product_id ON product_images (product_id);
CREATE INDEX idx_product_specifications_product_id ON product_specifications (product_id);
CREATE INDEX idx_product_brands_product_id ON product_brands (product_id);
CREATE INDEX idx_product_brands_brand_id ON product_brands (brand_id);
CREATE INDEX idx_product_categories_product_id ON product_categories (product_id);
CREATE INDEX idx_product_categories_cat_id ON product_categories (cat_id);
CREATE INDEX idx_cart_items_user_id ON cart_items (user_id);
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_voucher_id ON orders (voucher_id);
CREATE INDEX idx_orders_user_address_id ON orders (user_address_id);
CREATE INDEX idx_order_histories_order_id ON order_histories (order_id);
CREATE INDEX idx_order_histories_updated_by ON order_histories (updated_by);
CREATE INDEX idx_order_items_order_code ON order_items (order_code);
CREATE INDEX idx_user_vouchers_user_id ON user_vouchers (user_id);
CREATE INDEX idx_user_vouchers_voucher_id ON user_vouchers (voucher_id);
CREATE INDEX idx_aftersale_request_histories_request_id ON aftersale_request_histories (request_id);
CREATE INDEX idx_aftersale_request_histories_user_id ON aftersale_request_histories (user_id);
CREATE INDEX idx_transaction_logs_order_code ON transaction_logs (order_code);
CREATE INDEX idx_review_histories_user_id ON review_histories (user_id);
CREATE INDEX idx_review_histories_review_id ON review_histories (review_id);
CREATE INDEX idx_cart_items_variant ON cart_items (product_id, sku);
CREATE INDEX idx_reviews_variant ON reviews (product_id, sku);
CREATE INDEX idx_order_items_variant ON order_items (product_id, sku);
CREATE INDEX idx_aftersale_requests_variant ON aftersale_requests (product_id, sku);
