# Schema thông tin giao hàng và thanh toán

Code đã dùng `users.full_name`, `users.phone` và khóa có sẵn
`orders.user_address_id`. API tạo đơn nhận `addressId`, kiểm tra địa chỉ thuộc user.
Không cần thêm cột địa chỉ hoặc thông tin liên hệ vào đơn hàng.

Theo yêu cầu reset database local, baseline V1–V3 hiện không tạo các cột
`user_addresses.recipient`, `user_addresses.phone`, `orders.recipient`,
`orders.phone`, `orders.address_line` hoặc `orders.inventory_released`.
Đơn giữ `orders.user_address_id`, idempotency, snapshot sản phẩm/giá và timestamp.

V4 chuyển trạng thái phiên, mã payOS, QR và thông tin tài khoản vào `orders`.
`transaction_logs` lưu từng lần nhận webhook đã xác minh, gồm payload, header và
kết quả xử lý. Khóa dòng `orders` và trạng thái đã thanh toán ngăn ghi nhận tiền
hai lần. `payment_events` được xóa sau khi chuyển log cũ; payload/header cũ không
từng được lưu nên giữ NULL. Không lưu client ID, API key hoặc checksum key. Cấu hình payOS
được đọc từ `app.payment` trong `application.yml`, secret lấy từ biến môi trường.

Tên, số điện thoại và nội dung địa chỉ khi xem đơn được đọc từ hồ sơ/địa chỉ hiện tại.
Sửa hồ sơ hoặc địa chỉ sẽ thay đổi thông tin hiển thị trên đơn cũ. FK hiện có ngăn
xóa địa chỉ đã được đơn tham chiếu.

Database rỗng chạy lần lượt V1–V4; database đang dùng baseline V1–V3 chỉ chạy V4,
không reset dữ liệu. Baseline V1–V3 thay thế V1–V7 cũ theo yêu cầu
reset và không phải phương án nâng cấp dữ liệu; môi trường khác cần kế hoạch
chuyển dữ liệu riêng trước khi sử dụng.
