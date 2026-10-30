# Catalog API

Catalog follows the reset schema from the updated ERD. Flyway never inserts
demo data. A separate, opt-in development seed now populates the new relationships.

Each product has one optional brand via `products.brand_id`. The public `brands`
array is retained and contains zero or one entry. Specifications come from
`variant_specifications`, and the product response aggregates distinct values
from its valid variants. All selected specification filters must match the same
variant. Reviews resolve their product through the purchased order item and
variant, and only their latest history entry is returned.

## Development seed

After starting the backend once so Flyway initializes the database, run this from
another terminal in the backend directory:

```powershell
.\scripts\seed-catalog.ps1
```

The seed creates 100 sample products, 300 variants, 12 brands, 11 categories and
200 product images. Products have a direct brand/default variant, and variants
have their own image and technical specifications. SKUs use the reserved `NT26`
prefix. Product names, prices and configurations are illustrative development
data; images reference the existing frontend SVG illustrations.

Some variants and products are out of stock to exercise selection and availability.
No users, orders, reviews, promotions or aftersale requests are fabricated. The
SQL runs in one transaction under an advisory lock. Re-running skips existing
NT26 product families and preserves manual changes to stock, prices and defaults;
it does not repair partially deleted sample families or reset the database.
The PowerShell runner reads the ignored `.env` and transfers SQL as UTF-8 bytes.

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

Mỗi sản phẩm có `id`, `defaultVariantId`, `name`, `description`, `price`, `maxPrice`, `stock`,
`avgRating`, `reviewCount`, `soldCount`, `updatedAt`, `brands`, `categories`,
`specifications`, `variants`, `images`. ID ở API là số; frontend chuyển sang chuỗi
để giữ các component/URL hiện có. Bộ lọc và phân trang được xử lý tại backend.

Each variant additionally returns nullable `imageId` and its own `specifications`
array of `{ name, value }`. `defaultVariantId` is nullable and may identify an
out-of-stock variant; it is never silently replaced in the database. Existing
product-level aggregated specifications remain available for compatibility.
Frontend product details use the chosen variant's data; product cards use the
variant corresponding to the backend's displayed minimum price.

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

`CatalogIT` uses PostgreSQL Testcontainers with small synthetic test fixtures
in `src/test/resources/db/fixture/catalog.sql`. Every fixture transaction rolls
back. Tests cover empty databases, direct brand lookup, same-variant specification
filters, review/order-item joins, pagination, hidden products and API security.
The fixtures are not application seed data and never run during normal startup.
The seed regression runs the real SQL twice in its own `catalog_seed` test schema
and verifies graph completeness, no purchase/account data, no duplicates, and
preservation of edited prices and quantities.
