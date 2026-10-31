-- Optional development data. Re-running preserves edited rules and existing product assignments.
BEGIN;
SELECT pg_advisory_xact_lock(hashtext('nutee-discount-seed-2026')::BIGINT);

INSERT INTO promotions (id, code, applied_for, type, value, is_active, start_date, end_date) VALUES
('26000000-0000-0000-0000-000000000001', 'NT26_PROMO10', 'product', 'percent', 10, true, now() - interval '1 day', now() + interval '60 days'),
('26000000-0000-0000-0000-000000000002', 'NT26_SOON5', 'product', 'percent', 5, true, now() + interval '7 days', now() + interval '30 days'),
('26000000-0000-0000-0000-000000000003', 'NT26_EXPIRED', 'product', 'fixed', 50000, true, now() - interval '30 days', now() - interval '1 day'),
('26000000-0000-0000-0000-000000000004', 'NT26_PAUSED', 'product', 'fixed', 100000, false, now() - interval '1 day', now() + interval '60 days')
ON CONFLICT DO NOTHING;

WITH samples AS (
    SELECT p.id, row_number() OVER (ORDER BY p.id) AS number FROM products p
    WHERE EXISTS (SELECT 1 FROM product_variants v WHERE v.product_id = p.id AND v.sku LIKE 'NT26%')
), assignments AS (
    SELECT id, ('26000000-0000-0000-0000-' || lpad(number::TEXT, 12, '0'))::UUID AS promotion_id
    FROM samples WHERE number <= 4
)
UPDATE products p SET promotion_id = a.promotion_id, updated_at = now()
FROM assignments a WHERE p.id = a.id AND p.promotion_id IS NULL
    AND EXISTS (SELECT 1 FROM promotions rule WHERE rule.id = a.promotion_id);

INSERT INTO vouchers (id, code, applied_for, type, value, is_active, start_date, end_date, quantity, max_uses_per_user, min_order, max_discount) VALUES
('26100000-0000-0000-0000-000000000001', 'NT26_100K', 'all', 'fixed', 100000, true, now() - interval '1 day', now() + interval '60 days', 1000, 1, 1000000, NULL),
('26100000-0000-0000-0000-000000000002', 'NT26_10P', 'all', 'percent', 10, true, now() - interval '1 day', now() + interval '60 days', 500, 2, 500000, 200000),
('26100000-0000-0000-0000-000000000003', 'NT26_SOON', 'all', 'fixed', 30000, true, now() + interval '7 days', now() + interval '30 days', 100, 1, 300000, NULL),
('26100000-0000-0000-0000-000000000004', 'NT26_EXPIRED', 'all', 'fixed', 50000, true, now() - interval '30 days', now() - interval '1 day', 100, 1, 500000, NULL),
('26100000-0000-0000-0000-000000000005', 'NT26_PAUSED', 'all', 'percent', 5, false, now() - interval '1 day', now() + interval '60 days', 100, 1, 0, 100000),
('26100000-0000-0000-0000-000000000006', 'NT26_EMPTY', 'all', 'fixed', 10000, true, now() - interval '1 day', now() + interval '60 days', 0, 1, 0, NULL),
('26100000-0000-0000-0000-000000000007', 'NT26_PRIVATE', 'user', 'percent', 15, true, now() - interval '1 day', now() + interval '60 days', 100, 1, 1000000, 300000)
ON CONFLICT DO NOTHING;

-- No accounts are created. Give the private example to up to five existing active customers.
INSERT INTO user_vouchers (user_id, voucher_id)
SELECT u.id, '26100000-0000-0000-0000-000000000007'::UUID FROM users u
WHERE u.is_active AND u.role = 'CUSTOMER'
    AND EXISTS (SELECT 1 FROM vouchers v WHERE v.id = '26100000-0000-0000-0000-000000000007')
ORDER BY u.created_at, u.id LIMIT 5
ON CONFLICT DO NOTHING;
COMMIT;
