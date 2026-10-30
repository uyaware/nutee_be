-- Synthetic integration-test fixtures only; never loaded into the application database.
INSERT INTO users (id, username, email, full_name)
VALUES ('00000000-0000-0000-0000-000000000001', 'catalog-reviewer', 'reviewer@example.invalid', 'Test reviewer');
INSERT INTO brands (id, name) VALUES (1, 'ExampleTech'), (2, 'KeyWorks');
INSERT INTO categories (id, parent, name) VALUES
    (100, NULL, 'Electronics'), (101, 100, 'Laptops'), (102, 100, 'Phones'), (103, 100, 'Keyboards');
INSERT INTO products (id, brand_id, name, description, is_published) VALUES
    (1, 1, 'Test laptop', 'Laptop fixture', true),
    (2, 1, 'Test phone', 'Phone fixture', true),
    (3, 2, 'Test keyboard', 'Sold out fixture', true),
    (4, NULL, 'Unknown price', 'Not purchasable', true),
    (5, 2, 'Hidden product', 'Not published', false);
INSERT INTO product_images (id, product_id, image_url, is_primary) VALUES
    (11, 1, '/test-laptop.svg', true), (21, 2, '/test-phone.svg', true);
INSERT INTO product_variants (id, product_id, image_id, sku, price, quantity) VALUES
    (11, 1, 11, 'TEST-LAPTOP-8', 10000000, 2),
    (12, 1, 11, 'TEST-LAPTOP-16', 12000000, 3),
    (21, 2, 21, 'TEST-PHONE', 5000000, 4),
    (31, 3, NULL, 'TEST-KEYBOARD', 1000000, 0),
    (41, 4, NULL, 'TEST-UNKNOWN', NULL, NULL),
    (51, 5, NULL, 'TEST-HIDDEN', 1000, 5);
UPDATE products SET default_variant_id = 11 WHERE id = 1;
INSERT INTO product_categories (product_id, cat_id) VALUES (1, 101), (2, 102), (3, 103), (4, 101), (5, 103);
INSERT INTO variant_specifications (variant_id, specification_name, specification_value) VALUES
    (11, 'ram', '8 GB'), (11, 'storage', '256 GB'),
    (12, 'ram', '16 GB'), (12, 'storage', '512 GB'),
    (21, 'ram', '8 GB'), (21, 'storage', '128 GB'),
    (31, 'connection', 'USB-C'), (41, 'unknown-only', 'Not public'), (51, 'hidden-only', 'Not public');
INSERT INTO orders (id, order_code, user_id)
VALUES (101, 'TEST000001', '00000000-0000-0000-0000-000000000001');
INSERT INTO order_items (id, order_id, variant_id, quantity, calculated_price)
VALUES (201, 101, 11, 1, 9999999.50);
INSERT INTO reviews (id, order_item_id) VALUES (301, 201);
INSERT INTO review_histories (id, user_id, review_id, rating, comment, created_at) VALUES
    (401, '00000000-0000-0000-0000-000000000001', 301, 4, 'Earlier review', '2026-01-01T00:00:00Z'),
    (402, '00000000-0000-0000-0000-000000000001', 301, 5, 'Updated review', '2026-01-02T00:00:00Z');
