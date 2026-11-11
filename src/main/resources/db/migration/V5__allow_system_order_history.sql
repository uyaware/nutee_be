-- Hệ thống có thể thay đổi trạng thái đơn khi payOS xác nhận hoặc hết hạn.
-- Các bản ghi cũ và khóa ngoại tới người dùng được giữ nguyên.
ALTER TABLE order_histories ALTER COLUMN updated_by DROP NOT NULL;
