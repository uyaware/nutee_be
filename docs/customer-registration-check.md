# Kiểm tra đăng ký customer

Đã kiểm tra ngày **30/10/2026**, múi giờ `Asia/Ho_Chi_Minh`, bằng Chrome với
frontend `http://localhost:5173`, backend thật `http://localhost:8080` và
PostgreSQL `nutee` tại `localhost:5433`. Luồng này không giả lập API.

Tài khoản kiểm thử được giữ trong DB để đối chiếu:

- Email: `customer-register-20261030-aad07c@example.test`
- ID: `ac336e7b-e2bc-4e2b-a347-fdbc1ccd06a5`
- Vai trò: `CUSTOMER`, trạng thái hoạt động.

Kết quả:

| Kiểm tra | Kết quả |
| --- | --- |
| Gửi form đăng ký từ frontend | HTTP `201` |
| Đọc trực tiếp bảng `users` | Đúng 1 bản ghi, ID khớp phản hồi API |
| Mật khẩu lưu trong DB | BCrypt cost 12, không lưu mật khẩu thô |
| Cookie đăng nhập | access/refresh HttpOnly, Path `/api/v1` |
| Đăng xuất rồi đăng nhập lại bằng mật khẩu đã đăng ký | HTTP `200`, cùng user ID |
| Tải lại trang hồ sơ | Hiển thị đúng email từ `/auth/me` |
| Đăng ký lại cùng email | HTTP `409`, số bản ghi vẫn là 1 |
| Chặn localStorage/sessionStorage | Đăng ký và hồ sơ vẫn hoạt động |

Truy vấn đối chiếu:

```sql
SELECT id, username, email, role, is_active, created_at
FROM public.users
WHERE email = 'customer-register-20261030-aad07c@example.test';
```

Không ghi mật khẩu kiểm thử, hash mật khẩu hoặc JWT vào tài liệu.
