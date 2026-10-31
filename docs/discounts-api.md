# Voucher và promotion

Promotion giảm giá tự động của sản phẩm. Voucher giảm tiền hàng của đơn, áp dụng cho mọi
khách hàng hoặc khách hàng được cấp trong `user_vouchers`. Quy tắc đã chọn: promotion trước,
voucher trên tổng còn lại. Không giảm phí giao hàng, không cộng hai voucher.

## Quản trị

ADMIN còn hoạt động dùng `/api/v1/management/promotions` hoặc `/api/v1/management/vouchers`:

| Method | Path tương đối | Công dụng |
| --- | --- | --- |
| GET | `/` | Tìm `q`, lọc `status=all/active/paused/scheduled/expired`, `page` từ 0, `size` 1–100 |
| GET | `/{id}` | Chi tiết rule và đối tượng áp dụng |
| POST | `/` | Tạo, trả 201 |
| PUT | `/{id}` | Cập nhật nguyên tử |
| PATCH | `/{id}/active` | `{ "isActive": false }` để tạm dừng, true để bật lại |
| DELETE | `/{id}` | Xóa, trả 204 |
| GET | `/products?q=...` | Tìm sản phẩm, chỉ có ở promotion |
| GET | `/customers?q=...` | Tìm khách còn hoạt động, chỉ có ở voucher |

Các trường chung: `code`, `type` (`fixed` hoặc `percent`), `value`, `startsAt`, `endsAt`,
`isActive`. Mã được trim/viết hoa, 3–20 ký tự A–Z, số, `_`, `-`; không trùng trong cùng loại.
Mức giảm nguyên từ 1 đến 1 tỷ VND, phần trăm tối đa 100. Thời gian là ISO UTC, bắt đầu trước
kết thúc. Rule hiệu lực từ startsAt (bao gồm) đến endsAt (không bao gồm).

Promotion nhận `productIds` (1–200 ID không lặp). Mỗi sản phẩm thuộc một promotion theo FK
hiện có. API trả 409 nếu sản phẩm đang thuộc rule khác; cần gỡ liên kết cũ trước. Service khóa
rule và sản phẩm theo ID tăng dần. Xóa rule chỉ gỡ liên kết, không xóa sản phẩm/giá gốc.

Voucher thêm `scope` (`all` hoặc `user`), `minOrder`, `maxDiscount` (null nếu không giới hạn),
`quantity` (tổng hạn mức, 0 là hết lượt), `maxUsesPerUser` (tối thiểu 1), `userIds` (rỗng với
all, 1–200 UUID khách còn hoạt động với user). minOrder tính trên tổng sau promotion.
Lượt dùng đếm các đơn tham chiếu voucher trừ trạng thái cancelled/canceled. Không hạ tổng
hạn mức thấp hơn số lượt đang dùng; voucher có đơn tham chiếu không được xóa, có thể tạm dừng.
API quản trị kiểm tra ADMIN hiện tại trong DB, giữ cookie JWT/CSRF, không chỉ tin role JWT cũ.

## Giá và checkout

Catalog và giỏ trả giá hiện tại sau promotion trong `price`, cùng `originalPrice` và
`promotionCode` khi có giảm. Không sửa giá gốc trong product_variants. Bộ lọc/sắp xếp giá
dùng giá sau giảm tại database. Phần trăm làm tròn mức giảm xuống đến đồng; giá cuối không âm.

- GET `/api/v1/vouchers?page=0&size=20`: mã đang trong thời gian áp dụng, đúng phạm vi tài khoản,
  kèm `remaining`, `remainingPerUser`; không lộ danh sách người được cấp mã.
- POST `/api/v1/checkout/quote`: `{ "variantIds": [11, 12], "voucherCode": "WELCOME10" }`.
  Bỏ trống mã để bỏ voucher. ID phải thuộc giỏ chính chủ và còn đủ điều kiện mua. Giá/số lượng
  lấy lại từ backend; không nhận tổng tiền do client khai.

Quote trả `items`, `subtotal` (giá gốc), `promotionDiscount`, `voucherDiscount`, `discount`,
`total`, `voucherCode`, `shippingConfirmed=false`. total là tiền hàng sau giảm, chưa xác nhận
phí vận chuyển. Frontend làm mới mỗi 30 giây và khi giỏ/mã thay đổi; lỗi ẩn tổng cũ và giữ form.
Không tạo đơn, trừ lượt, hoặc giữ chỗ voucher khi xem trước. Khi triển khai tạo đơn sau này,
phải khóa/kiểm tra voucher và ghi đơn trong cùng transaction.

Mã không tồn tại hoặc không được cấp cùng trả 404. Thời hạn, đơn tối thiểu, hết lượt trả 409;
body sai 400, thiếu phiên 401, thiếu CSRF/quyền 403. Request ghi đều qua client chung.

## Migration và seed

V5 thêm vouchers.min_order, max_discount và unique index mã không phân biệt hoa thường.
Giữ nguyên dữ liệu, bảng và migration đã áp dụng. Sau khi backend áp dụng V5 và đã có catalog mẫu:

```powershell
.\scripts\seed-discounts.ps1
```

Seed chạy transaction/advisory lock, không chạy tự động qua Flyway. Có 4 promotion:
`NT26_PROMO10` (10%), `NT26_SOON5`, `NT26_EXPIRED`, `NT26_PAUSED`, gắn với bốn sản phẩm NT26
đầu tiên chưa thuộc rule khác. Có 7 voucher:

| Mã | Điều kiện chính |
| --- | --- |
| NT26_100K | Giảm 100.000đ, tổng sau promotion từ 1.000.000đ, 1 lượt/khách |
| NT26_10P | Giảm 10%, tối đa 200.000đ, tổng từ 500.000đ, 2 lượt/khách |
| NT26_SOON | Chưa bắt đầu |
| NT26_EXPIRED | Hết hạn |
| NT26_PAUSED | Tạm dừng |
| NT26_EMPTY | Hạn mức 0 |
| NT26_PRIVATE | Giảm 15%, tối đa 300.000đ, cấp tối đa 5 khách đang có |

Không tạo tài khoản, mật khẩu, đơn hoặc lịch sử dùng mã. Chạy lại bỏ qua rule đã có, giữ các
giá trị quản trị đã sửa và các assignment sản phẩm khác; không kéo dài thời hạn rule cũ.
Mốc thời gian mới dựa trên PostgreSQL lúc seed.

DiscountIT kiểm tra quyền, CSRF, CRUD, giá/bộ lọc sau giảm, làm tròn/tràn số, điều kiện mã,
phạm vi khách, quota theo đơn và seed chạy lặp trên PostgreSQL thật.
