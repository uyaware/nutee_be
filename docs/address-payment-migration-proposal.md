# Migration V7: bỏ thông tin giao hàng trùng

Code đã dùng `users.full_name`, `users.phone` và khóa có sẵn
`orders.user_address_id`. API tạo đơn nhận `addressId`, kiểm tra địa chỉ thuộc user.
Không cần thêm cột địa chỉ hoặc thông tin liên hệ vào đơn hàng.

Theo yêu cầu dùng version mới, giữ nguyên V4/V6 và bổ sung
`V7__remove_duplicate_delivery_contacts.sql`:

1. Xóa `user_addresses.recipient`, `user_addresses.phone`.
2. Xóa `orders.recipient`, `orders.phone`, `orders.address_line`.
   Giữ `orders.user_address_id` hiện có, không thêm hoặc đổi tên cột thành `address_id`.
3. Giữ các phần V6 đang được luồng checkout/thanh toán sử dụng: idempotency,
   snapshot sản phẩm/giá, trạng thái hoàn kho, timestamp, mã giao dịch,
   `payment_attempts` và `payment_events`.

Hai bảng thanh toán chỉ lưu trạng thái giao dịch và sự kiện webhook để chống xử lý
trùng/đối soát; không chứa client ID, API key hoặc checksum key. Cấu hình payOS
được đọc từ `app.payment` trong `application.yml`, secret lấy từ biến môi trường.

Tên, số điện thoại và nội dung địa chỉ khi xem đơn được đọc từ hồ sơ/địa chỉ hiện tại.
Sửa hồ sơ hoặc địa chỉ sẽ thay đổi thông tin hiển thị trên đơn cũ. FK hiện có ngăn
xóa địa chỉ đã được đơn tham chiếu.

V7 chủ đích xóa giá trị trong năm cột trùng nêu trên. Hồ sơ user, các dòng địa chỉ,
đơn hàng và khóa địa chỉ được giữ nguyên; không ghi đè hồ sơ từ bản sao cũ.
Database mới chạy lần lượt V1–V7; database đã ở V6 chỉ chạy thêm V7, không cần
repair/checksum reset. Migration được thực thi bởi Flyway khi backend khởi động.

`NuteeApplicationIT` kiểm tra nâng cấp từ V3 qua V6 tới V7, giữ dữ liệu hồ sơ/địa chỉ
và khóa địa chỉ trên đơn, xóa đủ năm cột trùng, validate và chạy lại không áp dụng lần hai.
