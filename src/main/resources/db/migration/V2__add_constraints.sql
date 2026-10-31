-- All primary keys, foreign keys, uniqueness, nullability and CHECK constraints.
-- PK/UNIQUE constraints create their own supporting indexes automatically.

ALTER TABLE users ADD CONSTRAINT pk_users PRIMARY KEY (id);
ALTER TABLE user_addresses ADD CONSTRAINT pk_user_addresses PRIMARY KEY (id);
ALTER TABLE auth_providers ADD CONSTRAINT pk_auth_providers PRIMARY KEY (id);
ALTER TABLE products ADD CONSTRAINT pk_products PRIMARY KEY (id);
ALTER TABLE product_images ADD CONSTRAINT pk_product_images PRIMARY KEY (id);
ALTER TABLE variant_specifications ADD CONSTRAINT pk_variant_specifications PRIMARY KEY (id);
ALTER TABLE brands ADD CONSTRAINT pk_brands PRIMARY KEY (id);
ALTER TABLE categories ADD CONSTRAINT pk_categories PRIMARY KEY (id);
ALTER TABLE product_categories ADD CONSTRAINT pk_product_categories PRIMARY KEY (product_id, cat_id);
ALTER TABLE cart_items ADD CONSTRAINT pk_cart_items PRIMARY KEY (id);
ALTER TABLE reviews ADD CONSTRAINT pk_reviews PRIMARY KEY (id);
ALTER TABLE orders ADD CONSTRAINT pk_orders PRIMARY KEY (id);
ALTER TABLE order_histories ADD CONSTRAINT pk_order_histories PRIMARY KEY (id);
ALTER TABLE vouchers ADD CONSTRAINT pk_vouchers PRIMARY KEY (id);
ALTER TABLE user_vouchers ADD CONSTRAINT pk_user_vouchers PRIMARY KEY (user_id, voucher_id);
ALTER TABLE promotions ADD CONSTRAINT pk_promotions PRIMARY KEY (id);
ALTER TABLE order_items ADD CONSTRAINT pk_order_items PRIMARY KEY (id);
ALTER TABLE aftersale_requests ADD CONSTRAINT pk_aftersale_requests PRIMARY KEY (id);
ALTER TABLE aftersale_request_histories ADD CONSTRAINT pk_aftersale_request_histories PRIMARY KEY (id);
ALTER TABLE transaction_logs ADD CONSTRAINT pk_transaction_logs PRIMARY KEY (id);
ALTER TABLE payment_events ADD CONSTRAINT pk_payment_events PRIMARY KEY (id);
ALTER TABLE product_variants ADD CONSTRAINT pk_product_variants PRIMARY KEY (id);
ALTER TABLE review_histories ADD CONSTRAINT pk_review_histories PRIMARY KEY (id);
ALTER TABLE event_logs ADD CONSTRAINT pk_event_logs PRIMARY KEY (id);

ALTER TABLE auth_providers ADD CONSTRAINT uk_auth_providers_provider_identity UNIQUE (provider, provider_user_id);
ALTER TABLE auth_providers ADD CONSTRAINT uk_auth_providers_user_provider UNIQUE (user_id, provider);
ALTER TABLE product_variants ADD CONSTRAINT uk_product_variants_sku UNIQUE (sku);
ALTER TABLE product_variants ADD CONSTRAINT uk_product_variants_product_id_id UNIQUE (product_id, id);
ALTER TABLE product_images ADD CONSTRAINT uk_product_images_product_id_id UNIQUE (product_id, id);
ALTER TABLE variant_specifications ADD CONSTRAINT uk_variant_specifications_name UNIQUE (variant_id, specification_name);
ALTER TABLE cart_items ADD CONSTRAINT uk_cart_items_user_variant UNIQUE (user_id, variant_id);
ALTER TABLE orders ADD CONSTRAINT uk_orders_order_code UNIQUE (order_code);
ALTER TABLE transaction_logs ADD CONSTRAINT uk_transaction_logs_order UNIQUE (order_id);
ALTER TABLE transaction_logs ADD CONSTRAINT uk_transaction_logs_gateway_code UNIQUE (gateway_order_code);
ALTER TABLE transaction_logs ADD CONSTRAINT uk_transaction_logs_external_id UNIQUE (external_id);

ALTER TABLE user_addresses ADD CONSTRAINT fk_user_addresses_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE auth_providers ADD CONSTRAINT fk_auth_providers_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE products ADD CONSTRAINT fk_products_promotion
    FOREIGN KEY (promotion_id) REFERENCES promotions (id);
ALTER TABLE products ADD CONSTRAINT fk_products_brand
    FOREIGN KEY (brand_id) REFERENCES brands (id);
ALTER TABLE products ADD CONSTRAINT fk_products_default_variant
    FOREIGN KEY (id, default_variant_id) REFERENCES product_variants (product_id, id);
ALTER TABLE product_images ADD CONSTRAINT fk_product_images_product
    FOREIGN KEY (product_id) REFERENCES products (id);
ALTER TABLE product_variants ADD CONSTRAINT fk_product_variants_product
    FOREIGN KEY (product_id) REFERENCES products (id);
ALTER TABLE product_variants ADD CONSTRAINT fk_product_variants_image
    FOREIGN KEY (product_id, image_id) REFERENCES product_images (product_id, id);
ALTER TABLE variant_specifications ADD CONSTRAINT fk_variant_specifications_variant
    FOREIGN KEY (variant_id) REFERENCES product_variants (id);
ALTER TABLE categories ADD CONSTRAINT fk_categories_parent
    FOREIGN KEY (parent) REFERENCES categories (id);
ALTER TABLE product_categories ADD CONSTRAINT fk_product_categories_product
    FOREIGN KEY (product_id) REFERENCES products (id);
ALTER TABLE product_categories ADD CONSTRAINT fk_product_categories_category
    FOREIGN KEY (cat_id) REFERENCES categories (id);
ALTER TABLE cart_items ADD CONSTRAINT fk_cart_items_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE cart_items ADD CONSTRAINT fk_cart_items_variant
    FOREIGN KEY (variant_id) REFERENCES product_variants (id);
ALTER TABLE reviews ADD CONSTRAINT fk_reviews_order_item
    FOREIGN KEY (order_item_id) REFERENCES order_items (id);
ALTER TABLE orders ADD CONSTRAINT fk_orders_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE orders ADD CONSTRAINT fk_orders_voucher
    FOREIGN KEY (voucher_id) REFERENCES vouchers (id);
ALTER TABLE orders ADD CONSTRAINT fk_orders_user_address
    FOREIGN KEY (user_address_id) REFERENCES user_addresses (id);
ALTER TABLE order_histories ADD CONSTRAINT fk_order_histories_order
    FOREIGN KEY (order_id) REFERENCES orders (id);
ALTER TABLE order_histories ADD CONSTRAINT fk_order_histories_updated_by
    FOREIGN KEY (updated_by) REFERENCES users (id);
ALTER TABLE order_items ADD CONSTRAINT fk_order_items_order
    FOREIGN KEY (order_id) REFERENCES orders (id);
ALTER TABLE order_items ADD CONSTRAINT fk_order_items_variant
    FOREIGN KEY (variant_id) REFERENCES product_variants (id);
ALTER TABLE user_vouchers ADD CONSTRAINT fk_user_vouchers_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE user_vouchers ADD CONSTRAINT fk_user_vouchers_voucher
    FOREIGN KEY (voucher_id) REFERENCES vouchers (id);
ALTER TABLE aftersale_requests ADD CONSTRAINT fk_aftersale_requests_order_item
    FOREIGN KEY (order_item_id) REFERENCES order_items (id);
ALTER TABLE aftersale_request_histories ADD CONSTRAINT fk_aftersale_request_histories_request
    FOREIGN KEY (request_id) REFERENCES aftersale_requests (id);
ALTER TABLE aftersale_request_histories ADD CONSTRAINT fk_aftersale_request_histories_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE transaction_logs ADD CONSTRAINT fk_transaction_logs_order
    FOREIGN KEY (order_id) REFERENCES orders (id);
ALTER TABLE review_histories ADD CONSTRAINT fk_review_histories_user
    FOREIGN KEY (user_id) REFERENCES users (id);
ALTER TABLE review_histories ADD CONSTRAINT fk_review_histories_review
    FOREIGN KEY (review_id) REFERENCES reviews (id);

ALTER TABLE products ADD CONSTRAINT ck_products_avg_rating CHECK (avg_rating BETWEEN 0 AND 5);
ALTER TABLE products ADD CONSTRAINT ck_products_sold_count CHECK (sold_count >= 0);
ALTER TABLE product_variants ADD CONSTRAINT ck_product_variants_sku CHECK (btrim(sku) <> '');
ALTER TABLE product_variants ADD CONSTRAINT ck_product_variants_price CHECK (price >= 0);
ALTER TABLE product_variants ADD CONSTRAINT ck_product_variants_quantity CHECK (quantity >= 0);
ALTER TABLE variant_specifications ADD CONSTRAINT ck_variant_specifications_name CHECK (btrim(specification_name) <> '');
ALTER TABLE variant_specifications ADD CONSTRAINT ck_variant_specifications_value CHECK (btrim(specification_value) <> '');
ALTER TABLE categories ADD CONSTRAINT ck_categories_parent CHECK (parent <> id);
ALTER TABLE cart_items ADD CONSTRAINT ck_cart_items_quantity CHECK (quantity > 0);
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_quantity CHECK (quantity > 0);
ALTER TABLE order_items ADD CONSTRAINT ck_order_items_calculated_price CHECK (calculated_price >= 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_order_code CHECK (btrim(order_code) <> '');
ALTER TABLE orders ADD CONSTRAINT ck_orders_total_amount CHECK (total_amount >= 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_sub_total CHECK (sub_total >= 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_shipping_fee CHECK (shipping_fee >= 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_discount_amount CHECK (discount_amount >= 0);
ALTER TABLE orders ADD CONSTRAINT ck_orders_payment_amount CHECK (payment_amount >= 0);
ALTER TABLE review_histories ADD CONSTRAINT ck_review_histories_rating CHECK (rating BETWEEN 1 AND 5);
ALTER TABLE transaction_logs ADD CONSTRAINT ck_transaction_logs_attempt_count CHECK (attempt_count >= 0);
ALTER TABLE transaction_logs ADD CONSTRAINT ck_transaction_logs_amount CHECK (amount > 0);
ALTER TABLE vouchers ADD CONSTRAINT ck_vouchers_min_order CHECK (min_order >= 0);
ALTER TABLE vouchers ADD CONSTRAINT ck_vouchers_max_discount CHECK (max_discount IS NULL OR max_discount > 0);

ALTER TABLE users
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN username SET NOT NULL,
    ALTER COLUMN email SET NOT NULL,
    ALTER COLUMN email_verified SET NOT NULL,
    ALTER COLUMN role SET NOT NULL,
    ALTER COLUMN is_active SET NOT NULL;
ALTER TABLE user_addresses
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE auth_providers
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN provider SET NOT NULL,
    ALTER COLUMN provider_user_id SET NOT NULL;
ALTER TABLE products
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE product_images
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN product_id SET NOT NULL,
    ALTER COLUMN is_primary SET NOT NULL;
ALTER TABLE variant_specifications
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN variant_id SET NOT NULL,
    ALTER COLUMN specification_name SET NOT NULL,
    ALTER COLUMN specification_value SET NOT NULL;
ALTER TABLE brands
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE categories
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE product_categories
    ALTER COLUMN product_id SET NOT NULL,
    ALTER COLUMN cat_id SET NOT NULL;
ALTER TABLE cart_items
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN variant_id SET NOT NULL,
    ALTER COLUMN quantity SET NOT NULL;
ALTER TABLE reviews
    ALTER COLUMN order_item_id SET NOT NULL;
ALTER TABLE orders
    ALTER COLUMN order_code SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE order_histories
    ALTER COLUMN order_id SET NOT NULL,
    ALTER COLUMN updated_by SET NOT NULL;
ALTER TABLE vouchers
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE user_vouchers
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN voucher_id SET NOT NULL;
ALTER TABLE promotions
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;
ALTER TABLE order_items
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN order_id SET NOT NULL,
    ALTER COLUMN variant_id SET NOT NULL,
    ALTER COLUMN quantity SET NOT NULL,
    ALTER COLUMN calculated_price SET NOT NULL;
ALTER TABLE aftersale_requests
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN order_item_id SET NOT NULL;
ALTER TABLE aftersale_request_histories
    ALTER COLUMN request_id SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE transaction_logs
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN order_id SET NOT NULL,
    ALTER COLUMN gateway_order_code SET NOT NULL,
    ALTER COLUMN provider SET NOT NULL,
    ALTER COLUMN amount SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN status SET NOT NULL,
    ALTER COLUMN expires_at SET NOT NULL;
ALTER TABLE payment_events
    ALTER COLUMN gateway_order_code SET NOT NULL,
    ALTER COLUMN external_id SET NOT NULL,
    ALTER COLUMN amount SET NOT NULL,
    ALTER COLUMN currency SET NOT NULL,
    ALTER COLUMN result_code SET NOT NULL,
    ALTER COLUMN decision SET NOT NULL,
    ALTER COLUMN received_at SET NOT NULL;
ALTER TABLE product_variants
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL,
    ALTER COLUMN product_id SET NOT NULL,
    ALTER COLUMN sku SET NOT NULL;
ALTER TABLE review_histories
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN user_id SET NOT NULL,
    ALTER COLUMN review_id SET NOT NULL;

COMMENT ON COLUMN cart_items.sku IS 'Optional cached SKU; variant_id is the sole variant foreign key.';
