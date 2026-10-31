-- Preserve existing rules and add the checkout limits needed by voucher management.
ALTER TABLE vouchers
    ADD COLUMN min_order BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN max_discount BIGINT;
ALTER TABLE vouchers ADD CONSTRAINT ck_vouchers_min_order CHECK (min_order >= 0);
ALTER TABLE vouchers ADD CONSTRAINT ck_vouchers_max_discount CHECK (max_discount IS NULL OR max_discount > 0);
CREATE UNIQUE INDEX uk_vouchers_code_ci ON vouchers (upper(btrim(code)));
CREATE UNIQUE INDEX uk_promotions_code_ci ON promotions (upper(btrim(code)));
