# Hồ sơ và địa chỉ tài khoản

Mọi endpoint dùng cookie access JWT và kiểm tra tài khoản còn hoạt động. Mọi request ghi
cần CSRF qua client chung. Người sở hữu lấy từ JWT, không nhận `userId` từ client.
Response chứa `Cache-Control: no-store` và không chứa mật khẩu, token hoặc Google subject.

| Method | Endpoint | Công dụng |
| --- | --- | --- |
| GET | `/api/v1/account/profile` | Hồ sơ, trạng thái Google và các địa chỉ của chính chủ |
| PUT | `/api/v1/account/profile` | Cập nhật tên, điện thoại; thêm địa chỉ tùy chọn cùng transaction |
| POST | `/api/v1/account/addresses` | Thêm địa chỉ, trả 201 |
| PUT | `/api/v1/account/addresses/{id}` | Sửa địa chỉ |
| PUT | `/api/v1/account/addresses/{id}/default` | Chọn địa chỉ mặc định |
| DELETE | `/api/v1/account/addresses/{id}` | Xóa địa chỉ |

Các thao tác thành công đều trả cùng cấu trúc hồ sơ mới:

```json
{
    "user": {
        "id": "00000000-0000-0000-0000-000000000001",
        "username": "example",
        "email": "example@example.invalid",
        "fullName": "Người dùng",
        "emailVerified": true,
        "imageUrl": null,
        "role": "CUSTOMER"
    },
    "phone": "0912345678",
    "googleLinked": false,
    "addresses": [
        {
            "id": 1,
            "provinceCity": "TP Hồ Chí Minh",
            "wardCommune": "Phường 1",
            "street": "12 Đường A",
            "isDefault": true
        }
    ]
}
```

Body cập nhật hồ sơ: `{ "fullName": "Tên mới", "phone": "0912345678" }`.
Tên sau trim dài 2–80 ký tự. Số điện thoại chấp nhận `0` + 9 chữ số hoặc `+84` +
9 chữ số, lưu dưới dạng 10 chữ số bắt đầu bằng `0`. Hồ sơ có thể bỏ trống/xóa điện thoại.
Email, username, quyền, trạng thái xác minh, ảnh và mật khẩu không thể sửa qua API này.

Body địa chỉ gồm `provinceCity`, `wardCommune`, `street`, `isDefault`.
Tất cả là bắt buộc; tỉnh/thành và phường/xã tối đa 255 ký tự,
đường 2–240 ký tự. Khi hoàn thiện hồ sơ, có thể gửi object này trong trường `address`
của PUT profile; cả hai phần cùng thành công hoặc cùng rollback. Không gửi lại `address`
khi chỉ muốn cập nhật thông tin tài khoản.

Địa chỉ đầu tiên tự làm mặc định. Chọn mặc định mới bỏ mặc định cũ; sửa địa chỉ mặc
định không tự bỏ trạng thái này. Xóa địa chỉ mặc định chọn địa chỉ còn lại có ID nhỏ
nhất. Thay đổi được tuần tự hóa bằng khóa dòng `users` và unique index hiện có.
Hai request thêm/chọn mặc định đồng thời không tạo hai địa chỉ mặc định.

ID không thuộc tài khoản hiện tại trả 404. Dữ liệu sai trả 400, thiếu phiên hoặc tài
khoản bị vô hiệu hóa trả 401, thiếu CSRF trả 403. Địa chỉ được đơn hàng tham chiếu
không bị xóa; FK giữ dữ liệu và API trả 409.

Địa chỉ không lưu tên/số điện thoại. Đặt hàng dùng `users.full_name`, `users.phone`
và lưu `addressId` vào cột có sẵn `orders.user_address_id`. Hồ sơ thiếu tên hoặc số
điện thoại hợp lệ phải được cập nhật trước khi đặt hàng. Baseline V1–V3 không tạo
các cột liên hệ trùng: xem [chi tiết schema](address-payment-migration-proposal.md).

Frontend: Hồ sơ ở `/account/profile`, địa chỉ ở `/account/addresses`, Google ở
`/account/security`. Checkout đọc địa chỉ đã lưu, chọn mặc định trước và gửi `addressId`.
Tên/số điện thoại hiển thị từ hồ sơ; muốn thay đổi thì sửa hồ sơ hoặc sổ địa chỉ.

`ProfileIT` kiểm tra quyền sở hữu, CSRF, validation, chuẩn hóa số, cập nhật nguyên tử,
địa chỉ mặc định, request đồng thời và địa chỉ đang được đơn tham chiếu với PostgreSQL.
`NuteeApplicationIT` kiểm tra baseline V1–V3, các cột liên hệ trùng không tồn tại,
và ràng buộc phiên thanh toán trong PostgreSQL.
