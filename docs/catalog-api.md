# Catalog API

Catalog follows the reset schema from the updated ERD. Flyway never inserts
demo data. A separate, opt-in development seed now populates the new relationships.

Each product has one optional brand via `products.brand_id`. The public `brands`
array is retained and contains zero or one entry. Specifications come from
`variant_specifications`, and the product response aggregates distinct values
from its valid variants. All selected specification filters must match the same
variant. Reviews resolve their product through the purchased order item and
variant, and only their latest history entry is returned.

## Backend organization

The storefront catalog is composed from three backend features:

```text
com.nutee.backend/
    product/
        controller/  service/  repository/  dto/
        entity/
        variant/
            entity/  repository/  dto/
            specification/
                entity/  repository/  dto/
        image/
            entity/  repository/  dto/
        review/
            entity/  repository/  dto/
    brand/
        controller/  service/  repository/  dto/  entity/
    category/
        controller/  service/  repository/  dto/  entity/
```

Product owns search, visibility checks and assembly of its response. Variant,
image, specification and review queries live with those product resources.
Brand/category have independent read controllers and lookup/error handling.
Related rows are still loaded in batches for a whole product page; splitting
the packages does not introduce a query per product or per variant. The variant
and review endpoints check visibility without loading every product relation.

This organization change preserves public URLs, JSON fields, OpenAPI operation
and schema names, validation rules, database migrations and seed data. Frontend
changes are not required. Child resources have no extra controllers/services
until they have their own API/business operations.

## Spring Data JPA

All business repositories use Spring Data JPA, preferring built-in repository
methods and derived queries. Brand/category reads use `findAll`/`findById`;
variants, images and specifications use derived queries with `Sort` and selective
`EntityGraph` fetching. User lookups also use derived queries, retaining
pessimistic row locks when changing sessions or consuming reset codes.

Product filtering uses
`JpaSpecificationExecutor` with Criteria subqueries for visibility, displayed
price, stock, and same-variant specification matching. Counting precedes a JPA
Slice so out-of-range pages are clamped without a duplicate count query.

Product IDs are paged before fetch-joining brand/category relationships. Variants,
their images/specifications, product images and review counts are loaded in
batches. No collection fetch join is used in a paged query, and API responses
contain DTOs rather than JPA entities. Tests check that the number of queries
stays bounded when a product page grows.

Category descendants are calculated from one JPA repository read with a visited
ID set, preserving recursive filtering even if category data contains a cycle.
The latest review uses JPQL with timestamp/ID tie-breaking and a username fallback
when the author's full name is blank. Explicit JPQL remains only for this
latest-review projection, grouped review counts and distinct specification facets;
each method documents why a derived query is insufficient. Purchase-price
snapshots remain unchanged.

The JPA model includes the existing user/provider entities and ten commerce
entities used by catalog and cart. `order.item.entity.OrderItem` is mapped so review
queries can traverse the purchase relationship; it does not add order mutation
APIs. Existing Flyway migrations and the seed SQL are unchanged. Hibernate remains
in `ddl-auto: validate`; JPA does not create or alter tables.

There is no application `JdbcTemplate`, direct JDBC, or native SQL repository
query. SQL is retained for Flyway, the opt-in seed and database test fixtures.
The mandatory JPA policy is recorded in `AGENTS.md`.

## Development seed

When the backend runs with the `local` profile, `CatalogSeedRunner` executes the
existing `scripts/seed-catalog.sql` automatically after Flyway and JPA initialize.
The SQL is packaged in the application jar, so startup does not require Docker or
`psql`. Set `CATALOG_SEED_ENABLED=false` in `backend/.env` and restart the backend
to disable automatic seeding; other profiles do not run it.

The seed creates 101 sample products, 301 variants, 12 brands, 11 categories and
201 product images. SKU `NT26PAYOS2000` is a payment test product priced at 2,000 VND with an initial quantity of 100. Products have a direct brand/default variant, and variants
have their own image and technical specifications. SKUs use the reserved `NT26`
prefix. Product names, prices and configurations are illustrative development
data; images reference the existing frontend SVG illustrations.

Some variants and products are out of stock to exercise selection and availability.
No users, orders, reviews, promotions or aftersale requests are fabricated. The
SQL runs in one transaction under an advisory lock. Re-running skips existing
NT26 product families and preserves manual changes to stock, prices and defaults;
it does not repair partially deleted sample families or reset the database.
The optional PowerShell seed runner uses Docker; the automatic runner reads the packaged SQL as UTF-8.

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
Cart đã có API theo variant; xem [Cart API](cart-api.md). Các màn hình chưa có
API quản trị/order vẫn giữ UI và trạng thái rỗng.

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
