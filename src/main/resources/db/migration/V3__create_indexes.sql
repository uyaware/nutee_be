-- Explicit lookup indexes, expression indexes and partial unique indexes only.
-- Do not duplicate leading-column indexes already supplied by V2 PK/UNIQUE constraints.

CREATE UNIQUE INDEX uk_users_username_ci ON users (lower(username));
CREATE UNIQUE INDEX uk_users_email_ci ON users (lower(email));
CREATE UNIQUE INDEX uk_users_password_reset_hash ON users (password_reset_hash)
    WHERE password_reset_hash IS NOT NULL;
CREATE UNIQUE INDEX uk_user_addresses_default ON user_addresses (user_id) WHERE is_default;
CREATE UNIQUE INDEX uk_product_images_primary ON product_images (product_id) WHERE is_primary;

CREATE INDEX idx_user_addresses_user_id ON user_addresses (user_id);
CREATE INDEX idx_categories_parent ON categories (parent);
CREATE INDEX idx_products_promotion_id ON products (promotion_id);
CREATE INDEX idx_products_brand_id ON products (brand_id);
CREATE INDEX idx_product_variants_image ON product_variants (image_id, product_id);
CREATE INDEX idx_product_categories_cat_id ON product_categories (cat_id);
CREATE INDEX idx_cart_items_variant_id ON cart_items (variant_id);
CREATE INDEX idx_reviews_order_item_id ON reviews (order_item_id);
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_voucher_id ON orders (voucher_id);
CREATE INDEX idx_orders_user_address_id ON orders (user_address_id);
CREATE INDEX idx_order_histories_order_id ON order_histories (order_id);
CREATE INDEX idx_order_histories_updated_by ON order_histories (updated_by);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_variant_id ON order_items (variant_id);
CREATE INDEX idx_user_vouchers_voucher_id ON user_vouchers (voucher_id);
CREATE INDEX idx_aftersale_requests_order_item_id ON aftersale_requests (order_item_id);
CREATE INDEX idx_aftersale_request_histories_request_id ON aftersale_request_histories (request_id);
CREATE INDEX idx_aftersale_request_histories_user_id ON aftersale_request_histories (user_id);
CREATE INDEX idx_transaction_logs_order_id ON transaction_logs (order_id);
CREATE INDEX idx_review_histories_user_id ON review_histories (user_id);
CREATE INDEX idx_review_histories_latest ON review_histories (review_id, created_at DESC, id DESC);
CREATE INDEX idx_products_default_variant_id ON products (default_variant_id)
    WHERE default_variant_id IS NOT NULL;
