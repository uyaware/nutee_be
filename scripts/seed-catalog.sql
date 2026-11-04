-- Development sample data for the current ERD; never run automatically by Flyway.
-- Re-running skips complete NT26 product families and preserves edited stock/prices.
BEGIN;
DO $$
DECLARE
    root_id INTEGER;
    parent_ids INTEGER[] := '{}';
    leaf_ids INTEGER[] := '{}';
    parent_names TEXT[] := ARRAY['Laptop', 'Điện thoại', 'Bàn phím'];
    leaf_names TEXT[] := ARRAY['Laptop văn phòng', 'Laptop gaming', 'Laptop đồ họa',
        'Điện thoại iOS', 'Điện thoại Android', 'Bàn phím cơ', 'Bàn phím không dây'];
    brand_names TEXT[] := ARRAY['Apple', 'Dell', 'ASUS', 'Lenovo', 'HP', 'Samsung',
        'Xiaomi', 'Google', 'Logitech', 'Keychron', 'Razer', 'Corsair'];
    laptop_brands TEXT[] := ARRAY['Apple', 'Dell', 'ASUS', 'Lenovo', 'HP'];
    phone_brands TEXT[] := ARRAY['Apple', 'Samsung', 'Xiaomi', 'Google'];
    keyboard_brands TEXT[] := ARRAY['Logitech', 'Keychron', 'Razer', 'Corsair'];
    brand_name TEXT;
    selected_brand_id INTEGER;
    parent_id INTEGER;
    found_id INTEGER;
    category_id INTEGER;
    item_id INTEGER;
    variant_id INTEGER;
    selected_default_variant_id INTEGER;
    primary_image_id INTEGER;
    overview_image_id INTEGER;
    kind_index INTEGER;
    kind TEXT;
    item_name TEXT;
    ram TEXT;
    storage_size TEXT;
    connection TEXT;
    color TEXT;
    configuration TEXT;
    base_price INTEGER;
    price_step INTEGER;
    variant_count INTEGER;
    inserted INTEGER := 0;
    i INTEGER;
    j INTEGER;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('nutee-catalog-seed-2026')::BIGINT);
    SELECT id INTO root_id FROM categories WHERE name = 'Đồ điện tử' AND parent IS NULL ORDER BY id LIMIT 1;
    IF root_id IS NULL THEN
        INSERT INTO categories (name) VALUES ('Đồ điện tử') RETURNING id INTO root_id;
    END IF;
    FOR i IN 1..3 LOOP
        SELECT id INTO found_id FROM categories WHERE name = parent_names[i] AND parent = root_id ORDER BY id LIMIT 1;
        IF found_id IS NULL THEN
            INSERT INTO categories (name, parent) VALUES (parent_names[i], root_id) RETURNING id INTO found_id;
        END IF;
        parent_ids[i] := found_id;
    END LOOP;
    FOR i IN 1..7 LOOP
        parent_id := parent_ids[CASE WHEN i <= 3 THEN 1 WHEN i <= 5 THEN 2 ELSE 3 END];
        SELECT id INTO found_id FROM categories WHERE name = leaf_names[i] AND parent = parent_id ORDER BY id LIMIT 1;
        IF found_id IS NULL THEN
            INSERT INTO categories (name, parent) VALUES (leaf_names[i], parent_id) RETURNING id INTO found_id;
        END IF;
        leaf_ids[i] := found_id;
    END LOOP;
    FOREACH brand_name IN ARRAY brand_names LOOP
        IF NOT EXISTS (SELECT 1 FROM brands WHERE lower(name) = lower(brand_name)) THEN
            INSERT INTO brands (name) VALUES (brand_name);
        END IF;
    END LOOP;

    FOR i IN 1..100 LOOP
        IF EXISTS (SELECT 1 FROM product_variants WHERE sku = 'NT26' || lpad(i::TEXT, 4, '0') || 'V01') THEN
            CONTINUE;
        END IF;
        kind_index := 1 + (i - 1) % 3;
        variant_count := 1 + (i - 1) % 5;
        IF kind_index = 1 THEN
            kind := 'laptop';
            brand_name := laptop_brands[1 + ((i - 1) / 3) % cardinality(laptop_brands)];
            category_id := leaf_ids[1 + ((i - 1) / 3) % 3];
            item_name := brand_name || ' Laptop Nova ' || lpad(i::TEXT, 3, '0');
            base_price := 14000000 + (i % 15) * 850000;
            price_step := 1500000;
        ELSIF kind_index = 2 THEN
            kind := 'smartphone';
            brand_name := phone_brands[1 + ((i - 1) / 3) % cardinality(phone_brands)];
            category_id := leaf_ids[CASE WHEN brand_name = 'Apple' THEN 4 ELSE 5 END];
            item_name := brand_name || ' Smartphone Nova ' || lpad(i::TEXT, 3, '0');
            base_price := 6500000 + (i % 15) * 500000;
            price_step := 700000;
        ELSE
            kind := 'keyboard';
            brand_name := keyboard_brands[1 + ((i - 1) / 3) % cardinality(keyboard_brands)];
            category_id := leaf_ids[6 + ((i - 1) / 3) % 2];
            item_name := brand_name || ' Keyboard Nova ' || lpad(i::TEXT, 3, '0');
            base_price := 900000 + (i % 12) * 120000;
            price_step := 150000;
        END IF;
        SELECT id INTO selected_brand_id FROM brands WHERE lower(name) = lower(brand_name) ORDER BY id LIMIT 1;
        INSERT INTO products (brand_id, name, description, avg_rating, sold_count, is_published)
        VALUES (selected_brand_id, item_name, 'Dữ liệu mẫu NUTEE để trải nghiệm danh mục và lựa chọn cấu hình.', 0, 0, true)
        RETURNING id INTO item_id;
        INSERT INTO product_categories (product_id, cat_id) VALUES (item_id, category_id);
        INSERT INTO product_images (product_id, image_url, is_primary)
        VALUES (item_id, '/images/' || kind || '.svg', true) RETURNING id INTO primary_image_id;
        INSERT INTO product_images (product_id, image_url, is_primary)
        VALUES (item_id, '/images/' || kind || '-overview.svg', false) RETURNING id INTO overview_image_id;
        selected_default_variant_id := NULL;
        FOR j IN 1..variant_count LOOP
            color := (ARRAY['Đen', 'Bạc', 'Trắng'])[1 + (j - 1) % 3];
            ram := (ARRAY['8 GB', '16 GB', '32 GB'])[1 + (j - 1) % 3];
            storage_size := (ARRAY['256 GB', '512 GB', '1 TB'])[1 + (j - 1) % 3];
            connection := (ARRAY['USB-C', 'Bluetooth', 'USB-C / Bluetooth'])[1 + (j - 1) % 3];
            configuration := CASE WHEN kind_index IN (1, 2)
                THEN ram || ' RAM / ' || storage_size || ' / ' || color
                ELSE connection || ' / ' || color END;
            INSERT INTO product_variants (product_id, image_id, sku, price, quantity)
            VALUES (item_id, CASE WHEN j % 2 = 1 THEN primary_image_id ELSE overview_image_id END,
                'NT26' || lpad(i::TEXT, 4, '0') || 'V' || lpad(j::TEXT, 2, '0'),
                base_price + (j - 1) * price_step,
                CASE WHEN i % 17 = 0 OR (i % 13 = 0 AND j = 1) THEN 0 ELSE 5 + ((i + j) * 7) % 20 END)
            RETURNING id INTO variant_id;
            IF selected_default_variant_id IS NULL THEN selected_default_variant_id := variant_id; END IF;
            INSERT INTO variant_specifications (variant_id, specification_name, specification_value) VALUES
                (variant_id, 'kind', kind), (variant_id, 'color', color), (variant_id, 'configuration', configuration);
            IF kind_index IN (1, 2) THEN
                INSERT INTO variant_specifications (variant_id, specification_name, specification_value) VALUES
                    (variant_id, 'ram', ram), (variant_id, 'storage', storage_size),
                    (variant_id, 'screen', CASE WHEN kind_index = 1 THEN '14 inch' ELSE '6.5 inch' END);
            ELSE
                INSERT INTO variant_specifications (variant_id, specification_name, specification_value) VALUES
                    (variant_id, 'connection', connection),
                    (variant_id, 'switch', (ARRAY['Red', 'Brown', 'Blue'])[1 + (j - 1) % 3]);
            END IF;
        END LOOP;
        UPDATE products SET default_variant_id = selected_default_variant_id WHERE id = item_id;
        inserted := inserted + 1;
    END LOOP;
    -- Sản phẩm riêng để thử thanh toán thật; không đặt lại giá/tồn kho sau khi đã tạo.
    IF NOT EXISTS (SELECT 1 FROM product_variants WHERE sku = 'NT26PAYOS2000') THEN
        INSERT INTO products (name, description, avg_rating, sold_count, is_published)
        VALUES ('Sản phẩm kiểm thử thanh toán 2.000đ', 'Sản phẩm mẫu để kiểm thử quy trình thanh toán.', 0, 0, true)
        RETURNING id INTO item_id;
        INSERT INTO product_categories (product_id, cat_id) VALUES (item_id, root_id);
        INSERT INTO product_images (product_id, image_url, is_primary)
        VALUES (item_id, '/images/keyboard.svg', true) RETURNING id INTO primary_image_id;
        INSERT INTO product_variants (product_id, image_id, sku, price, quantity)
        VALUES (item_id, primary_image_id, 'NT26PAYOS2000', 2000, 100) RETURNING id INTO variant_id;
        INSERT INTO variant_specifications (variant_id, specification_name, specification_value) VALUES
            (variant_id, 'kind', 'keyboard'), (variant_id, 'configuration', 'Kiểm thử thanh toán');
        UPDATE products SET default_variant_id = variant_id WHERE id = item_id;
    END IF;

    RAISE NOTICE 'Inserted % products; existing NT26 families were preserved.', inserted;
END $$;
COMMIT;

SELECT count(DISTINCT product_id) AS seeded_products, count(*) AS seeded_variants
FROM product_variants WHERE sku LIKE 'NT26%';
