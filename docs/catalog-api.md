# Catalog API và seed

Catalog đọc trực tiếp schema hiện có, không sửa Flyway V1/V2 và không tạo bảng song song.
Controller xử lý HTTP; service xử lý phân trang/404; repository dùng SQL bind parameter.
Các quan hệ được tải theo lô, không tạo truy vấn cho từng sản phẩm trong một trang.

## Seed

Từ thư mục backend, khi PostgreSQL của Compose đã chạy:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/seed-catalog.ps1
```

Script đọc `.env` hiện có, copy SQL nguyên byte vào container rồi chạy bằng `psql`
với `ON_ERROR_STOP=1`. Toàn bộ seed nằm trong transaction và có advisory lock.
Seed không chạy tự động trong production hoặc Flyway.

| Dữ liệu seed | Số lượng |
| --- | --- |
| Sản phẩm công khai | 100 |
| Variants | 300; mỗi sản phẩm 1–5 variants |
| Brand | 12 |
| Category | 11; gồm danh mục cha và con |
| Ảnh | 200; mỗi sản phẩm 1 ảnh chính và 1 ảnh tổng quan |

SKU seed dùng tiền tố `NTSD`, tối đa 11 ký tự, phù hợp `VARCHAR(16)`.
Chạy lại nhận diện sản phẩm qua SKU đầu tiên, không tạo trùng và không đặt lại giá/tồn kho.
Brand/category hiện có được dùng lại theo tên và quan hệ cha. Không xóa user, order hay dữ liệu khác.

Tên, giá và thông số là dữ liệu mẫu minh họa. Ảnh tham chiếu các SVG đã có trong frontend.
Không tạo review, đơn hàng hoặc số lượt bán giả; rating/sold_count ban đầu là 0.
5 sản phẩm hết hàng để kiểm tra trạng thái hết kho; 95 sản phẩm còn hàng.

Schema variant hiện chỉ có `product_id`, `sku`, `price`, `quantity` và timestamps.
API giữ nguyên cấu trúc này; thông số nằm ở cấp sản phẩm trong `product_specifications`,
không tự thêm bảng/cột thuộc tính variant ngoài ERD.

## API đọc công khai

Tất cả URL dùng tiền tố `/api/v1`. Các GET dưới đây không cần đăng nhập;
cookie JWT hết hạn không chặn duyệt catalog. Cơ chế JWT/CSRF của API khác vẫn được giữ.

| Method / path | Kết quả |
| --- | --- |
| GET `/products` | Danh sách sản phẩm có phân trang và các quan hệ |
| GET `/products/{id}` | Chi tiết sản phẩm đã xuất bản |
| GET `/products/{id}/variants` | SKU, giá và quantity từng variant |
| GET `/products/{id}/reviews` | Bản sửa mới nhất của mỗi review, phân trang |
| GET `/products/facets` | Brand, category, các giá trị thông số để dựng bộ lọc |
| GET `/brands` | Danh sách brand |
| GET `/brands/{id}` | Chi tiết brand |
| GET `/categories` | Danh sách category có `parentId` |
| GET `/categories/{id}` | Chi tiết category |

Sản phẩm chưa xuất bản, không tồn tại hoặc không có variant hợp lệ trả `404` khi đọc chi tiết.
Lỗi query trả `400` theo ProblemDetail. API không cung cấp CRUD quản trị trong phần này.

## Tìm kiếm và lọc

```text
/api/v1/products?page=0&size=12&sort=price-asc
/api/v1/products?q=Nova&category=2&brand=1&brand=3&inStock=true
/api/v1/products?minPrice=10000000&maxPrice=20000000&spec.ram=16%20GB
```

| Query | Ý nghĩa |
| --- | --- |
| `q` | Tối đa 160 ký tự; tìm tên, mô tả, SKU, brand và giá trị thông số |
| `category` | ID dương; gồm cả sản phẩm thuộc danh mục con |
| `brand` | ID dương, có thể lặp nhiều lần; tối đa 20 brand |
| `minPrice`, `maxPrice` | Giá nguyên không âm; min không vượt max |
| `inStock` | `true` hoặc `false` |
| `spec.<name>` | Giá trị thông số; OR trong một tên, AND giữa các tên; có thể lặp giá trị |
| `sort` | `featured`, `price-asc`, `price-desc`, `name`, `newest` |
| `page` | Bắt đầu từ 0; vượt số trang được đưa về trang cuối |
| `size` | 1–100, mặc định 12 |

`q` tìm chuỗi nguyên văn; `%` và `_` không trở thành wildcard do người dùng điều khiển.
ID brand/category phải lấy từ API, không dùng slug/ID của prototype cũ.

Giá hiển thị/lọc là giá thấp nhất của variant còn hàng; khi cả sản phẩm hết hàng,
lấy giá thấp nhất của các variant hợp lệ. `stock` là tổng quantity; mỗi variant
vẫn trả giá và quantity riêng. Variant có giá/quantity âm hoặc null không được công khai.

Danh sách dùng `PageResponse`:

```json
{
  "content": [],
  "page": 0,
  "size": 12,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

Mỗi sản phẩm có `id`, `name`, `description`, `price`, `maxPrice`, `stock`,
`avgRating`, `reviewCount`, `soldCount`, `updatedAt`, `brands`, `categories`,
`specifications`, `variants`, `images`. ID ở API là số; frontend chuyển sang chuỗi
để giữ các component/URL hiện có. Bộ lọc và phân trang được xử lý tại backend.

## Frontend

Trang chủ, danh sách, tìm kiếm, bộ lọc, ảnh, breadcrumb và chi tiết dùng
`src/modules/catalog/api/catalogService.ts` qua HTTP client chung.
Chọn variant đổi giá và giới hạn quantity theo dữ liệu API. Không dùng localStorage,
không fallback sang seed frontend hoặc dữ liệu rỗng khi API lỗi.
Các màn hình chưa có API quản trị/cart/order vẫn giữ UI và trạng thái rỗng.

## Kiểm tra

```powershell
mvn.cmd test
mvn.cmd '-Dtest=CatalogIT' test
```

`CatalogIT` dùng PostgreSQL Testcontainers riêng và script seed thật: kiểm tra seed
idempotent, 1–5 variants, tìm kiếm/lọc, phân trang, chặn sản phẩm ẩn và giữ bảo mật các API khác.
Đã kiểm tra thêm frontend với backend/DB thật khi browser storage bị chặn, gồm tìm SKU,
lọc kết hợp, chọn variant cập nhật giá/tồn kho và giao diện mobile.
