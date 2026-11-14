DO $$
DECLARE
    parents integer[] := '{}';
    children integer[] := '{}';
    names text[] := ARRAY['Laptop','Điện thoại','Bàn phím','Chuột','Tai nghe'];
    subnames text[] := ARRAY['Laptop văn phòng','Laptop gaming','Điện thoại iOS','Điện thoại Android','Bàn phím cơ','Bàn phím không dây','Chuột gaming','Chuột không dây','Tai nghe không dây','Tai nghe có dây'];
    brand_names text[] := ARRAY['Apple','Samsung','ASUS','Lenovo','Logitech','Keychron','Sony','Razer','Dell','Xiaomi'];
    kinds text[] := ARRAY['laptop','smartphone','keyboard','mouse','headphone'];
    images text[] := ARRAY[
        'https://images.unsplash.com/photo-1496181133206-80ce9b88a853?w=640&h=480&fit=crop',
        'https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?w=640&h=480&fit=crop',
        'https://images.unsplash.com/photo-1587829741301-dc798b83add3?w=640&h=480&fit=crop',
        'https://images.unsplash.com/photo-1527814050087-3793815479db?w=640&h=480&fit=crop',
        'https://images.unsplash.com/photo-1505740420928-5e560c06d30e?w=640&h=480&fit=crop'];
    alternate_images text[] := ARRAY[
        'https://images.unsplash.com/photo-1541807084-5c52b6b3adef?w=640&h=480&fit=crop',
        'https://images.unsplash.com/photo-1592750475338-74b7b21085ab?w=640&h=480&fit=crop',
        'https://images.unsplash.com/photo-1595225476474-87563907a212?w=640&h=480&fit=crop',
        'https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=640&h=480&fit=crop',
        'https://images.unsplash.com/photo-1484704849700-f032a568e944?w=640&h=480&fit=crop'];
    customer uuid := 'af000000-0000-4000-8000-000000000001';
    customer2 uuid := 'af000000-0000-4000-8000-000000000002';
    promo uuid := 'af000000-0000-4000-8000-000000000010';
    voucher uuid := 'af000000-0000-4000-8000-000000000011';
    brand_id integer; product_id integer; image_id integer; alternate_image_id integer; variant_id integer; category_id integer; item_name text;
    address_id integer; order_id integer; item_id integer; review_id integer; request_id integer;
    product_ids integer[] := '{}'; variant_ids integer[] := '{}'; image_urls text[] := '{}';
    prices integer[] := '{}'; product_names text[] := '{}';
    kind_index integer; i integer; j integer; amount integer; rating integer;
    owner_id uuid; status text; img text;
BEGIN
    FOR i IN 1..5 LOOP
        INSERT INTO categories(name) VALUES (names[i]) RETURNING id INTO category_id;
        parents[i] := category_id;
    END LOOP;
    FOR i IN 1..10 LOOP
        INSERT INTO categories(name, parent) VALUES (subnames[i], parents[(i + 1) / 2]) RETURNING id INTO category_id;
        children[i] := category_id;
        INSERT INTO brands(name) VALUES (brand_names[i]);
    END LOOP;
    INSERT INTO promotions(id, code, applied_for, type, value, is_active, start_date, end_date)
    VALUES (promo, 'WELCOME10', 'product', 'percent', 10, true, now() - interval '1 day', now() + interval '180 days');
    INSERT INTO vouchers(id, code, applied_for, type, value, is_active, start_date, end_date, quantity, max_uses_per_user, min_order, max_discount)
    VALUES (voucher, 'NUTEE100K', 'all', 'fixed', 100000, true, now() - interval '1 day', now() + interval '180 days', 100, 1, 1000000, NULL);
    INSERT INTO users(id, username, email, full_name, phone, email_verified, role, is_active)
    VALUES (customer, 'khachhang1', 'khachhang1@nutee.local', 'Nguyễn Minh Anh', '0901234567', false, 'CUSTOMER', true),
           (customer2, 'khachhang2', 'khachhang2@nutee.local', 'Trần Hoàng Nam', '0901234568', false, 'CUSTOMER', true);
    INSERT INTO user_addresses(user_id, province_city, ward_commune, street, is_default)
    VALUES (customer, 'TP. Hồ Chí Minh', 'Phường Bến Nghé', '12 Nguyễn Huệ', true) RETURNING id INTO address_id;
    INSERT INTO user_addresses(user_id, province_city, ward_commune, street, is_default)
    VALUES (customer2, 'Hà Nội', 'Phường Tràng Tiền', '25 Tràng Tiền', true);
    INSERT INTO user_vouchers(user_id, voucher_id) VALUES (customer, voucher);

    FOR i IN 1..50 LOOP
        kind_index := 1 + (i - 1) % 5;
        SELECT id INTO brand_id FROM brands WHERE name = brand_names[1 + (i - 1) % 10];
        amount := CASE kind_index WHEN 1 THEN 12500000 + i * 120000 WHEN 2 THEN 6500000 + i * 80000 WHEN 3 THEN 850000 + i * 15000 WHEN 4 THEN 350000 + i * 12000 ELSE 650000 + i * 14000 END;
        img := images[kind_index];
        INSERT INTO products(brand_id, promotion_id, name, description, avg_rating, sold_count, is_published)
        VALUES (brand_id, CASE WHEN i % 4 = 0 THEN promo ELSE NULL END,
            brand_names[1 + (i - 1) % 10] || ' ' || names[kind_index] || ' NUTEE ' || lpad(i::text, 2, '0'),
            'Sản phẩm mẫu dành cho trải nghiệm cửa hàng NUTEE.', 0, 0, true)
        RETURNING id, name INTO product_id, item_name;
        product_names[i] := item_name;
        INSERT INTO product_categories(product_id, cat_id) VALUES (product_id, children[(kind_index - 1) * 2 + 1 + (i / 5) % 2]);
        INSERT INTO product_images(product_id, image_url, is_primary) VALUES (product_id, img, true) RETURNING id INTO image_id;
        INSERT INTO product_images(product_id, image_url, is_primary) VALUES (product_id, alternate_images[kind_index], false) RETURNING id INTO alternate_image_id;
        FOR j IN 1..3 LOOP
            INSERT INTO product_variants(product_id, image_id, sku, price, quantity)
            VALUES (product_id, CASE WHEN j = 2 THEN alternate_image_id ELSE image_id END, 'NUT' || lpad(i::text, 4, '0') || 'V' || j, amount + (j - 1) * 200000, 20 + i)
            RETURNING id INTO variant_id;
            INSERT INTO variant_specifications(variant_id, specification_name, specification_value)
            VALUES (variant_id, 'kind', kinds[kind_index]), (variant_id, 'color', CASE j WHEN 3 THEN 'Trắng' ELSE 'Đen' END);
            IF kind_index IN (1, 2) THEN
                INSERT INTO variant_specifications(variant_id, specification_name, specification_value)
                VALUES (variant_id, 'ram', CASE j WHEN 2 THEN '16 GB' ELSE '8 GB' END),
                    (variant_id, 'storage', CASE j WHEN 2 THEN '512 GB' ELSE '256 GB' END);
            ELSE
                INSERT INTO variant_specifications(variant_id, specification_name, specification_value)
                VALUES (variant_id, 'connection', CASE j WHEN 2 THEN 'Không dây' ELSE 'Có dây' END);
            END IF;
            IF j = 1 THEN
                UPDATE products SET default_variant_id = variant_id WHERE id = product_id;
                variant_ids[i] := variant_id;
            END IF;
        END LOOP;
        product_ids[i] := product_id; image_urls[i] := img; prices[i] := amount;
    END LOOP;

    FOR i IN 1..20 LOOP
        owner_id := CASE WHEN i % 2 = 0 THEN customer ELSE customer2 END;
        SELECT id INTO address_id FROM user_addresses WHERE user_id = owner_id AND is_default;
        amount := prices[i];
        status := CASE WHEN i <= 15 THEN 'delivered' WHEN i <= 17 THEN 'pending' WHEN i <= 19 THEN 'shipping' ELSE 'cancelled' END;
        INSERT INTO orders(order_code, user_id, user_address_id, total_amount, sub_total, shipping_fee, discount_amount,
            promotion_discount, voucher_discount, status, payment_method, payment_amount, payment_status, created_at, updated_at)
        VALUES ('NT' || lpad(i::text, 8, '0'), owner_id, address_id, amount + 25000, amount, 25000, 0, 0, 0, status,
            'cod', CASE WHEN status = 'delivered' THEN amount + 25000 ELSE 0 END,
            CASE WHEN status = 'delivered' THEN 'paid' ELSE 'unpaid' END, now() - (i || ' days')::interval, now() - (i || ' days')::interval)
        RETURNING id INTO order_id;
        INSERT INTO order_items(order_id, variant_id, quantity, calculated_price, warranty_expired_at, product_name, sku_snapshot, image_url, configuration, original_price)
        VALUES (order_id, variant_ids[i], 1, amount, now() + interval '1 year', product_names[i], 'NUT' || lpad(i::text, 4, '0') || 'V1', image_urls[i], 'Màu đen', amount)
        RETURNING id INTO item_id;
        INSERT INTO order_histories(order_id, updated_by, order_status, occurred_at)
        VALUES (order_id, owner_id, 'pending', now() - (i || ' days')::interval),
            (order_id, owner_id, status, now() - (i || ' days')::interval + interval '1 hour');
        IF status = 'delivered' THEN
            UPDATE products SET sold_count = sold_count + 1 WHERE id = product_ids[i];
            IF i <= 10 THEN
                rating := CASE WHEN i % 3 = 0 THEN 4 ELSE 5 END;
                INSERT INTO reviews(order_item_id) VALUES (item_id) RETURNING id INTO review_id;
                INSERT INTO review_histories(user_id, review_id, rating, comment, created_at)
                VALUES (owner_id, review_id, rating, 'Sản phẩm đúng mô tả, giao hàng nhanh.', now() - (i || ' days')::interval + interval '2 hours');
                UPDATE products SET avg_rating = rating WHERE id = product_ids[i];
            END IF;
            IF i <= 4 THEN
                INSERT INTO aftersale_requests(order_item_id, type, reason, description)
                VALUES (item_id, CASE WHEN i % 2 = 0 THEN 'return' ELSE 'warranty' END,
                    'Sản phẩm cần được kiểm tra', 'Khách hàng yêu cầu hỗ trợ sau khi nhận hàng.') RETURNING id INTO request_id;
                INSERT INTO aftersale_request_histories(request_id, user_id, request_status, occurred_at)
                VALUES (request_id, owner_id, 'pending', now() - interval '1 day');
            END IF;
        END IF;
    END LOOP;
END $$;
