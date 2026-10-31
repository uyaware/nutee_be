# Schema thông tin giao hàng và thanh toán

Code đã dùng `users.full_name`, `users.phone` và khóa có sẵn
`orders.user_address_id`. API tạo đơn nhận `addressId`, kiểm tra địa chỉ thuộc user.
Không cần thêm cột địa chỉ hoặc thông tin liên hệ vào đơn hàng.

Theo yêu cầu reset database local, baseline V1–V3 hiện không tạo các cột
`user_addresses.recipient`, `user_addresses.phone`, `orders.recipient`,
`orders.phone`, `orders.address_line` hoặc `orders.inventory_released`.
Đơn giữ `orders.user_address_id`, idempotency, snapshot sản phẩm/giá và timestamp.

`transaction_logs` lưu trạng thái phiên thanh toán; `payment_events` lưu sự kiện
webhook để chống xử lý
trùng/đối soát; không chứa client ID, API key hoặc checksum key. Cấu hình payOS
được đọc từ `app.payment` trong `application.yml`, secret lấy từ biến môi trường.

Tên, số điện thoại và nội dung địa chỉ khi xem đơn được đọc từ hồ sơ/địa chỉ hiện tại.
Sửa hồ sơ hoặc địa chỉ sẽ thay đổi thông tin hiển thị trên đơn cũ. FK hiện có ngăn
xóa địa chỉ đã được đơn tham chiếu.

Database rỗng chạy lần lượt V1–V3. Chuỗi này thay thế V1–V7 cũ theo yêu cầu
reset và không phải phương án nâng cấp dữ liệu; môi trường khác cần kế hoạch
chuyển dữ liệu riêng trước khi sử dụng.
