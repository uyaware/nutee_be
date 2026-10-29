-- Sample catalog only. Re-running skips existing NTSD product families and never resets stock/prices.
-- Existing schema and user/order/auth data are not modified.
BEGIN;
DO $$
DECLARE
    root_id integer;
    parent_ids integer[] := '{}';
    leaf_ids integer[] := '{}';
    parent_names text[] := ARRAY['Laptop', 'Điện thoại', 'Bàn phím'];
    leaf_names text[] := ARRAY['Laptop văn phòng', 'Laptop gaming', 'Laptop đồ họa',
        'Điện thoại iOS', 'Điện thoại Android', 'Bàn phím cơ', 'Bàn phím không dây'];
    brand_names text[] := ARRAY['Apple', 'Dell', 'ASUS', 'Lenovo', 'HP', 'Samsung',
        'Xiaomi', 'Google', 'Logitech', 'Keychron', 'Razer', 'Corsair'];
    laptop_brands text[] := ARRAY['Apple', 'Dell', 'ASUS', 'Lenovo', 'HP'];
    phone_brands text[] := ARRAY['Apple', 'Samsung', 'Xiaomi', 'Google'];
    keyboard_brands text[] := ARRAY['Logitech', 'Keychron', 'Razer', 'Corsair'];
    item_name text;
    brand_name text;
    item_id integer;
    brand_id integer;
    category_id integer;
    found_id integer;
    parent_id integer;
    kind_index integer;
    kind text;
    ram text;
    storage_size text;
    connection text;
    base_price integer;
    price_step integer;
    variant_count integer;
    i integer;
    j integer;
    inserted integer := 0;
BEGIN
    PERFORM pg_advisory_xact_lock(hashtext('nutee-catalog-seed-v1')::bigint);
    SELECT id INTO root_id FROM categories WHERE name = 'Đồ điện tử' AND parent IS NULL ORDER BY id LIMIT 1;
    IF root_id IS NULL THEN
        INSERT INTO categories(name) VALUES ('Đồ điện tử') RETURNING id INTO root_id;
    END IF;
    FOR i IN 1..3 LOOP
        SELECT id INTO found_id FROM categories WHERE name = parent_names[i] AND parent = root_id ORDER BY id LIMIT 1;
        IF found_id IS NULL THEN
            INSERT INTO categories(name, parent) VALUES (parent_names[i], root_id) RETURNING id INTO found_id;
        END IF;
        parent_ids[i] := found_id;
    END LOOP;
    FOR i IN 1..7 LOOP
        parent_id := parent_ids[CASE WHEN i <= 3 THEN 1 WHEN i <= 5 THEN 2 ELSE 3 END];
        SELECT id INTO found_id FROM categories WHERE name = leaf_names[i] AND parent = parent_id ORDER BY id LIMIT 1;
        IF found_id IS NULL THEN
            INSERT INTO categories(name, parent) VALUES (leaf_names[i], parent_id) RETURNING id INTO found_id;
        END IF;
        leaf_ids[i] := found_id;
    END LOOP;
    FOREACH brand_name IN ARRAY brand_names LOOP
        IF NOT EXISTS (SELECT 1 FROM brands WHERE lower(name) = lower(brand_name)) THEN
            INSERT INTO brands(name) VALUES (brand_name);
        END IF;
    END LOOP;

    FOR i IN 1..100 LOOP
        IF EXISTS (SELECT 1 FROM product_variants WHERE sku = 'NTSD' || lpad(i::text, 4, '0') || 'V01') THEN
            CONTINUE;
        END IF;
        kind_index := 1 + (i - 1) % 3;
        variant_count := 1 + (i - 1) % 5;
        IF kind_index = 1 THEN
            kind := 'laptop';
            brand_name := laptop_brands[1 + ((i - 1) / 3) % cardinality(laptop_brands)];
            category_id := leaf_ids[1 + ((i - 1) / 3) % 3];
            item_name := brand_name || ' Laptop Nova ' || lpad(i::text, 3, '0');
            ram := (ARRAY['8 GB', '16 GB', '32 GB'])[1 + ((i - 1) / 3) % 3];
            storage_size := (ARRAY['256 GB', '512 GB', '1 TB'])[1 + ((i - 1) / 6) % 3];
            base_price := 14000000 + (i % 15) * 850000;
            price_step := 1500000;
        ELSIF kind_index = 2 THEN
            kind := 'smartphone';
            brand_name := phone_brands[1 + ((i - 1) / 3) % cardinality(phone_brands)];
            category_id := leaf_ids[CASE WHEN brand_name = 'Apple' THEN 4 ELSE 5 END];
            item_name := brand_name || ' Smartphone Nova ' || lpad(i::text, 3, '0');
            ram := (ARRAY['6 GB', '8 GB', '12 GB'])[1 + ((i - 1) / 3) % 3];
            storage_size := (ARRAY['128 GB', '256 GB', '512 GB'])[1 + ((i - 1) / 6) % 3];
            base_price := 6500000 + (i % 15) * 500000;
            price_step := 700000;
        ELSE
            kind := 'keyboard';
            brand_name := keyboard_brands[1 + ((i - 1) / 3) % cardinality(keyboard_brands)];
            category_id := leaf_ids[6 + ((i - 1) / 3) % 2];
            item_name := brand_name || ' Keyboard Nova ' || lpad(i::text, 3, '0');
            connection := (ARRAY['USB-C', 'Bluetooth'])[1 + ((i - 1) / 3) % 2];
            base_price := 900000 + (i % 12) * 120000;
            price_step := 150000;
        END IF;
        SELECT id INTO brand_id FROM brands WHERE lower(name) = lower(brand_name) ORDER BY id LIMIT 1;
        INSERT INTO products(name, description, avg_rating, sold_count, is_published)
        VALUES (item_name, 'Thiết kế hiện đại cho công việc và trải nghiệm hằng ngày.', 0, 0, true)
        RETURNING id INTO item_id;
        INSERT INTO product_brands(product_id, brand_id) VALUES (item_id, brand_id);
        INSERT INTO product_categories(product_id, cat_id) VALUES (item_id, category_id);
        FOR j IN 1..variant_count LOOP
            INSERT INTO product_variants(product_id, sku, price, quantity)
            VALUES (item_id, 'NTSD' || lpad(i::text, 4, '0') || 'V' || lpad(j::text, 2, '0'),
                base_price + (j - 1) * price_step, CASE WHEN i % 17 = 0 THEN 0 ELSE 10 + ((i + j) * 7) % 50 END);
        END LOOP;
        INSERT INTO product_images(product_id, image_url, is_primary) VALUES
            (item_id, '/images/' || kind || '.svg', true),
            (item_id, '/images/' || kind || '-overview.svg', false);
        INSERT INTO product_specifications(product_id, specification_name, specification_value) VALUES
            (item_id, 'kind', kind),
            (item_id, 'color', (ARRAY['Đen', 'Bạc', 'Trắng'])[1 + ((i - 1) / 3) % 3]);
        IF kind_index IN (1, 2) THEN
            INSERT INTO product_specifications(product_id, specification_name, specification_value) VALUES
                (item_id, 'configuration', ram || ' RAM / ' || storage_size),
                (item_id, 'ram', ram), (item_id, 'storage', storage_size),
                (item_id, 'screen', CASE WHEN kind_index = 1 THEN '14 inch' ELSE '6.5 inch' END);
        ELSE
            INSERT INTO product_specifications(product_id, specification_name, specification_value) VALUES
                (item_id, 'configuration', 'Bàn phím cơ / ' || connection),
                (item_id, 'connection', connection),
                (item_id, 'switch', (ARRAY['Red', 'Brown', 'Blue'])[1 + ((i - 1) / 3) % 3]);
        END IF;
        inserted := inserted + 1;
    END LOOP;
    RAISE NOTICE 'Inserted % products. Existing NTSD products were preserved.', inserted;
END $$;
COMMIT;

SELECT count(DISTINCT product_id) AS seeded_products, count(*) AS seeded_variants
FROM product_variants WHERE sku LIKE 'NTSD%';
