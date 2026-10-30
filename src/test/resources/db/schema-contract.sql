-- Business relationships exercised with the isolated catalog fixture.
DO $$
BEGIN
    BEGIN
        UPDATE products SET default_variant_id = 21 WHERE id = 1;
        RAISE EXCEPTION 'A default variant from another product was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        UPDATE product_variants SET image_id = 21 WHERE id = 11;
        RAISE EXCEPTION 'A variant image from another product was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        UPDATE product_images SET product_id = 2, is_primary = false WHERE id = 11;
        RAISE EXCEPTION 'An image was moved away from its linked variants';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    INSERT INTO cart_items (user_id, variant_id, quantity) VALUES
        ('00000000-0000-0000-0000-000000000001', 11, 1),
        ('00000000-0000-0000-0000-000000000001', 12, 1);
    BEGIN
        INSERT INTO cart_items (user_id, variant_id, quantity)
        VALUES ('00000000-0000-0000-0000-000000000001', 11, 2);
        RAISE EXCEPTION 'A duplicate user/variant cart row was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE cart_items SET quantity = 0 WHERE variant_id = 11;
        RAISE EXCEPTION 'A zero cart quantity was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        UPDATE product_variants SET sku = 'TEST-LAPTOP-8' WHERE id = 12;
        RAISE EXCEPTION 'A duplicate business SKU was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        UPDATE product_variants SET price = -1 WHERE id = 11;
        RAISE EXCEPTION 'A negative catalog price was accepted';
    EXCEPTION WHEN check_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO product_images (product_id, image_url, is_primary)
        VALUES (1, '/another-primary.svg', true);
        RAISE EXCEPTION 'Multiple primary product images were accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO variant_specifications (variant_id, specification_name, specification_value)
        VALUES (11, 'ram', 'Duplicate');
        RAISE EXCEPTION 'A duplicate variant specification was accepted';
    EXCEPTION WHEN unique_violation THEN NULL;
    END;
    INSERT INTO aftersale_requests (order_item_id, reason) VALUES (201, 'Test request');
    BEGIN
        INSERT INTO aftersale_requests (order_item_id) VALUES (999999);
        RAISE EXCEPTION 'An aftersale request without a purchase was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO aftersale_requests (reason) VALUES ('Missing order item');
        RAISE EXCEPTION 'A NULL aftersale purchase reference was accepted';
    EXCEPTION WHEN not_null_violation THEN NULL;
    END;
    BEGIN
        INSERT INTO reviews (order_item_id) VALUES (999999);
        RAISE EXCEPTION 'A review without a purchased order item was accepted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    BEGIN
        DELETE FROM product_variants WHERE id = 11;
        RAISE EXCEPTION 'A variant with purchase/history references was deleted';
    EXCEPTION WHEN foreign_key_violation THEN NULL;
    END;
    UPDATE product_variants SET sku = 'RENAMED-LAPTOP', price = 123 WHERE id = 11;
    IF (SELECT count(*) FROM cart_items WHERE variant_id IN (11, 12)) <> 2
        OR (SELECT calculated_price FROM order_items WHERE id = 201) <> 9999999.50
        OR (SELECT count(*) FROM reviews review JOIN order_items item ON item.id = review.order_item_id
            WHERE item.variant_id = 11) <> 1
        OR (SELECT count(*) FROM aftersale_requests WHERE order_item_id = 201) <> 1 THEN
        RAISE EXCEPTION 'Catalog edits changed purchase prices or internal identity relationships';
    END IF;
END $$;
