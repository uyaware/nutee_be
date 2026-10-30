# Schema decisions

Source: [NUTEE_EERD_ERD.drawio](https://drive.google.com/file/d/1hmp8WFtYhvkK1jkg758HYj5xaVGpoJLy/view),
ERD page `i9jcEZAXrc9SuSMo_mB8`, modified 2026-10-30T12:27:02.048Z.
The ERD page, rather than the conceptual EERD page, defines this baseline.

## Authorized reset

The user explicitly requested replacement of all migrations and deletion of the
local database and catalog seed data. The old V1/V2/V3 migration chain has been
removed. This baseline is for a new, empty database; it is not an upgrade path
for any database that applied the previous migration chain. The configured local
`nutee` database on `localhost:5433` was dropped, including its Flyway history.
The catalog seed SQL and PowerShell runner were removed. No production/demo data
is inserted by the new migrations.

Other deployments must be deliberately reset or given a separately reviewed data
migration before adopting this baseline. Do not use Flyway repair, automatic
baselining, or disabled validation to disguise the changed migration history.
After this reset, subsequent schema changes must use new migration versions.

## Migration separation

| Migration | Responsibility |
| --- | --- |
| `V1__init_schema.sql` | Creates all 23 ERD tables, columns, sequences via SERIAL, and column defaults. No INSERT, ALTER, REFERENCES, or explicit index/relational constraint declarations. |
| `V2__add_constraints.sql` | Named PK, FK, UNIQUE and CHECK constraints, plus explicit NOT NULL requirements. All constraints are validated immediately on the empty baseline. |
| `V3__create_indexes.sql` | Explicit lookup indexes, expression uniqueness and partial unique indexes. Avoids redundant indexes where a V2 PK/UNIQUE index already supplies the needed prefix. |

PostgreSQL automatically creates supporting indexes for V2 PK/UNIQUE constraints;
these belong with their constraints. SERIAL also implies column nullability and
a sequence/default in V1. Expression/partial uniqueness (case-insensitive user
identities, reset hashes, default addresses, primary images) uses explicit unique
indexes in V3 because it cannot be declared as a plain UNIQUE table constraint.

`docs/schema.sql` includes these three files for psql review/test bootstrap:

```powershell
psql <connection options> --single-transaction --file=docs/schema.sql
```

Use Flyway to initialize application databases so its history is recorded.
`clean-disabled`, `validate-on-migrate`, and `baseline-on-migrate` keep their
existing safe settings. No automatic database-drop or seed hook was added.

## Updated ERD relationships

The latest ERD supersedes the previous product/variant upgrade proposal. There
are no legacy migration columns or backfills in this fresh baseline.

* A product has one optional `brand_id`; the `product_brands` junction is removed.
* A product may choose `default_variant_id`. Composite FK `(id, default_variant_id)`
  to `product_variants(product_id, id)` enforces ownership. NULL supports drafts
  and creation before a variant exists; no default is selected automatically.
* A variant has one optional `image_id`. Composite FK `(product_id, image_id)` to
  `product_images(product_id, id)` prevents cross-product images, including on
  updates to the image's parent product. Multiple variants can share an image.
  Product-wide primary-image flags remain on `product_images`; there is no
  `variant_images` association table or variant display-order column in this ERD.
* Specifications belong to `variant_specifications`, with one nonblank value per
  `(variant_id, specification_name)`. Names are case-sensitive. The latest ERD
  has no shared `product_specifications` table.
* Variant `id` stays the internal INTEGER PK and SKU stays a unique, required
  `VARCHAR(16)` business identifier. Variant `price` is INTEGER as shown by this
  updated ERD; the previous BIGINT/is_active proposal is not carried forward.
* Cart rows use a SERIAL `id` PK and UNIQUE `(user_id, variant_id)`. Internal
  relationships use `variant_id`. The diagram's redundant cart SKU marker is
  represented as an optional cached SKU, not a second variant FK; SKU renaming
  cannot break the cart. Its length is aligned to `VARCHAR(16)`.
* Orders use SERIAL `id` and unique required `order_code VARCHAR(10)`.
  Order items, histories and transaction logs reference INTEGER `order_id`.
  Order lines use a SERIAL `id`, required variant FK, positive quantity and
  nonnegative `calculated_price NUMERIC(18,2)`. No old product/SKU pair remains.
* Reviews reference a purchased `order_item_id`; their product is derived through
  `order_items.variant_id`. Review histories retain authors and revisions.
  Aftersale requests also require an order item, with no nullable legacy exception.
* The updated order-item shape contains only its price snapshot; the prior
  product-name/SKU snapshot extension is superseded by the supplied ERD.
* Source attributes on `event_logs` remain attributes; no extra analytics FKs
  were inferred. Foreign keys use default NO ACTION behavior; there are no
  cascading deletes of purchases, requests, or review history.

## Naming, typing and existing authentication

Names use plural lowercase snake_case. Corrected source names are:
`User_Adress` -> `user_addresses`, `shipping_free` -> `shipping_fee`,
`paymemt_status` -> `payment_status`, `timestampt`/`occured_at` -> `occurred_at`,
and `Review.order_item` -> `reviews.order_item_id`.

`orders.user_address_id` is INTEGER to match the address SERIAL PK, correcting
the diagram's UUID mismatch. `products.avg_rating` is NUMERIC(2,1), since the
source NUMERIC(1,1) cannot represent 5.0; a CHECK restricts values to 0..5.

Existing authentication behavior is preserved: `users.refresh_token`,
`password_reset_hash`, and `password_reset_expires_at` remain despite being absent
from the diagram. Usernames/emails are case-insensitively unique, provider
identities have both established unique constraints, and password-reset hashes
are unique when present. Required authentication fields, timestamps, cookie/JWT
behavior and user/provider JPA mappings retain their current contract. No auth
sessions, new providers, default users or initial admin accounts are added.

Mandatory relationships and timestamps are NOT NULL; optional promotion, brand,
default variant, variant image, order voucher/address and unspecified descriptive
attributes remain nullable. Known prices/stock/counts are nonnegative; cart and
order quantities are required and positive. Review ratings are 1..5. Created and
updated timestamps default to now(); there are no update triggers.

## Catalog compatibility and verification

Catalog SQL now reads the direct product brand, variant specifications, and
reviews through order items. The public response retains its `brands` array
(zero or one entry) and distinct aggregated variant specification values. Every
selected specification must match the same valid variant. No API paths, cookies,
authentication rules, frontend flows, or localStorage behavior are changed.

The old 100-product catalog seed and its runner were removed during the reset.
The subsequent user request adds a new opt-in `scripts/seed-catalog.sql` and runner
for the current schema: direct brand/default variant, per-variant image and
specifications. It inserts no accounts or purchase/history data and is not a
Flyway migration. See `docs/catalog-api.md` for seed usage and idempotency limits.
The API also exposes `defaultVariantId`, variant `imageId` and per-variant
specifications so the frontend can render the selected configuration accurately.
`CatalogIT` uses small synthetic fixtures under `src/test/resources/db/fixture/catalog.sql`
inside rolled-back test transactions, and separately verifies an empty catalog.
These fixtures never run in application initialization or Flyway. Schema tests
check ownership, cart uniqueness, purchase-linked reviews/aftersale requests,
SKU-independent references and preserved decimal purchase prices.

Run the suite using JDK 21 and Docker with PostgreSQL 16 containers:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

The integration schema contains 23 domain tables plus `flyway_schema_history`.
Flyway must apply exactly three migrations, validate their checksums, and apply
zero migrations on a second run. The separate migration smoke-test fixture is
retained because it tests Flyway infrastructure and is not an application schema
migration or catalog seed.
