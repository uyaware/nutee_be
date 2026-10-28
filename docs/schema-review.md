# NUTEE ERD review

> Historical planning review. The database and authentication implementation now
> follows the subsequent user request. See [schema decisions](schema-decisions.md)
> and [README](../README.md) for applied migrations and delivered behavior. Proposals
> below that go beyond those decisions remain future work.

Review date: 2026-10-28. Status: planning proposal; no domain migrations or application code changed.

Source: [NUTEE_EERD_ERD.drawio](https://drive.google.com/file/d/1hmp8WFtYhvkK1jkg758HYj5xaVGpoJLy/view), modified 2026-10-26T16:54:04.304Z. Reviewed ERD page `i9jcEZAXrc9SuSMo_mB8`; 24 table shapes were extracted. The EERD page is conceptual and was not used as a substitute for the ERD field definitions.

Evidence boundary: the diagram provides table names, attributes, types, and some PK/FK/unique markers. It does not establish all nullability, defaults, indexes, CHECK constraints, or deletion policies. Those are marked as decisions to specify, rather than asserted to be absent from a live database. Docker access was denied in this session, so the deployed database state has not been verified.

## Authentication findings

| Existing table / field | Observed definition | Proposed change | Priority |
| --- | --- | --- | --- |
| `User.password` | `VARCHAR(255)` | Rename to `password_hash`; persist only an adaptive password hash. Keep nullable only if future provider-only accounts are supported. | Required for login |
| `User.email` | `VARCHAR(255)` | Require canonical email, trim and normalize consistently, and enforce a case-insensitive unique database index. Define nullability explicitly. | Required for login |
| `User.username` | `VARCHAR(255)` | Optional profile handle for email-only login; allow NULL and enforce uniqueness if used. Do not require the current email/password registration form to supply it. | Required contract decision |
| `User.role` | `VARCHAR(255)` | Retain one role per user for MVP; NOT NULL, default CUSTOMER, CHECK in CUSTOMER/STAFF/ADMIN. Map server-side to ROLE_* authorities. | Required for RBAC |
| `User.is_active` | `BOOLEAN` | NOT NULL, default true; enforce at login, refresh, and authenticated requests. | Required for login |
| `User.email_verified` | `BOOLEAN` | NOT NULL, default false. MVP permits login without verification; later sensitive operations may require verified email. Never mark true without proof. | Required contract decision |
| `User.token_version` | Not listed | Add BIGINT NOT NULL DEFAULT 0 for account-wide invalidation. Carry as access-token claim `ver`. | Required by proposed revocation policy |
| `User.password_changed_at` | Not listed | Add nullable TIMESTAMPTZ for credential history and support. | Recommended |
| `User.last_login_at` | Not listed | Add nullable TIMESTAMPTZ; update only on successful credential login. | Recommended |
| `User.full_name` | `VARCHAR(100)` | Permit NULL at registration; required profile data belongs to profile completion/checkout. | Frontend compatibility |
| `User.phone` | `VARCHAR(10)` | Widen, for example VARCHAR(32); normalize phone format if used. Current width cannot represent international prefixes reliably. | Before profile integration |
| `User.created_at`, `updated_at` | TIMESTAMPTZ | Define NOT NULL UTC-aware timestamps, creation defaults, and update behavior. | Required |
| `Auth_Provider` | User/provider/provider_user_id already present | Specify UNIQUE(provider, provider_user_id), indexed user FK, controlled provider values, and account-linking ownership rules before social login. | Later OAuth milestone |
| Session / refresh history | No matching table in the 24-table ERD | Add `auth_sessions` and `refresh_tokens` as defined in the implementation plan. | Required for refresh/logout |
| Auth security audit | `Event_Log` is product/analytics oriented | Add a dedicated `auth_audit_events` table or equivalent durable security audit sink. Never store tokens/passwords. | Required before production |
| Reset / verification tokens | No matching table | Add purpose-specific, hashed, single-use challenge records when password recovery / email verification is implemented. | Later milestone |

`refresh_token` and `refresh_tokens_used` should not be columns on User in this design. One account can have multiple devices and separate customer/backoffice sessions. Store one session family per login and one hashed token row per rotation; `consumed_at` and `replaced_by_id` retain reuse evidence. Raw tokens and access JWTs are not persisted.

Separate `roles`, `permissions`, `user_roles`, and `role_permissions` tables are optional for configurable/multiple roles. They are not prerequisites for the three fixed roles and one-role-per-user model already used in the frontend.

## B2C schema issues and follow-up decisions

| Table(s) | Observed issue or missing field | Proposed resolution | Delivery boundary |
| --- | --- | --- | --- |
| `User_Adress` / `Order.user_address_id` | Address PK is SERIAL; order FK is UUID. Table name also misspells Address. | Rename to `user_addresses`; choose one key type and match it everywhere (INTEGER is the minimal change). Specify FK and ownership. | Before order migrations |
| `User_Adress` | No recipient name or recipient phone listed. | Add `recipient_name`, `recipient_phone`; clarify address line and optional label. A partial unique index on user_id WHERE is_default enforces at most one default. | Before address APIs |
| `User_Adress` / `Order` | A reference to a mutable user address alone does not preserve delivery details. | Store an immutable recipient/address snapshot on the order, even when the original address is changed or removed. | Before checkout |
| `Product.avg_rating` | NUMERIC(1,1) cannot store a rating such as 4.5. | Use NUMERIC(3,2) with CHECK 0 <= avg_rating <= 5; define rating_count and how aggregates are refreshed. | Before catalog migrations |
| `Product_Variant.price` / order money fields | Variant price is INT, order prices are NUMERIC(18,2). | Standardize a money representation and VND rounding policy. Proposal: NUMERIC(18,2) with nonnegative constraints and server-calculated totals. | Before catalog/checkout |
| `Product_Variant` and SKU references | Variant and Order_Item use VARCHAR(16); Cart_Item, Review, Aftersale_Request use VARCHAR(32). | Prefer variant_id INTEGER FKs for operational links; retain SKU as unique business identifier and order snapshot. If SKU remains an FK, align types and lengths. | Before dependent migrations |
| `Cart_Item` | PK(user_id, product_id) cannot hold two variants of the same product. | Use PK(user_id, variant_id), positive quantity, and indexed FKs. Clarify guest carts and merge idempotency in a separate cart milestone. | Before cart APIs |
| `Order_Item` | PK(order_code, product_id) has the same variant limitation; composite PK row is labeled UUID despite mixed UUID/INT components. | Prefer an independent order_item ID plus UNIQUE(order_code, variant_id), or an accurately typed composite key. Add variant FK and immutable SKU/name/unit-price/discount snapshots. | Before checkout |
| `Order` | No created_at / updated_at listed; shipping_free and paymemt_status appear misspelled. | Add timestamps; rename shipping_fee/payment_status/subtotal consistently; specify nonnegative amounts and allowable status transitions. | Before order migrations |
| `Order` / `Order_Item` | Total arithmetic, currency, discounts, address snapshots, inventory reservation, and idempotency are not fully specified. | Define currency and snapshot policy; add checkout idempotency persistence and atomic stock/reservation behavior. Clients never supply authoritative totals or paid status. | Before checkout |
| `Order_History` | order_id differs from Order.order_code; timestampt is misspelled. | Align FK naming; use occurred_at; preserve previous/new status, actor, and optional reason. Define system-actor representation. | Before workflow APIs |
| `Review` | Only id, product_id, SKU listed. Ownership and purchase eligibility cannot be established from these fields. | Add user_id, order_item_id, current rating/comment, moderation status, created_at/updated_at; CHECK rating 1..5. Enforce the agreed review-per-purchase rule. | Before review writes |
| `Review_History` | History has user_id/rating/comment, but ownership and edit actor semantics are unclear. | Keep immutable edits; distinguish review owner from actor if admins can moderate. Do not rely on latest history as an unstated ownership rule. | Before review writes |
| `Aftersale_Request` | No user_id, order_item_id, quantity, or current status listed. Product/SKU alone does not prove entitlement. | Add purchaser and purchased-item links, requested quantity, current status, eligibility timestamps; constrain requested quantities against delivered items. | Before aftersale APIs |
| `Aftersale_Request.images` | TEXT does not define a structured gallery. | Use attachment rows or a validated JSONB list; never store raw uploads in this text field. | Before attachments |
| `Aftersale_Request_History` | timestampt typo and current-state derivation unclear. | Rename occurred_at; distinguish actor, prior/new status, and reason. Current request status and history update atomically. | Before aftersale workflow |
| `Transaction_Log` / `Order` | No durable payment attempt/provider reference/webhook event uniqueness fields listed. | Add payment_transactions/payment_events with provider references, amount/currency/status, unique webhook event ID and payment attempt identity. Logs alone cannot enforce payment idempotency. | Before payment integration |
| `Transaction_Log.raw_body`, request_header | Potentially sensitive raw provider payload/header storage. | Allowlist/redact stored metadata, verify provider signatures, and set retention/access policy. Never persist Authorization/Cookie/signing secrets. | Before payment integration |
| `Voucher` / `User_Voucher` | max_uses_per_user exists, but User_Voucher records entitlement only; no redemption count/history listed. | Add voucher_redemptions tied to user/order with idempotency and concurrent use-limit enforcement; specify discount caps/minimum order value if required. | Before promotions/checkout |
| `Voucher`, `Promotion` | applied_for/type are unconstrained strings in the visible diagram; percentage/fixed amount semantics and stacking are unspecified. | Specify controlled values, temporal checks, amount representation, eligibility, caps, and atomic consumption rules. | Before discount APIs |
| `Product.promotion_id` | Encodes at most one direct promotion per product; broader scope/stacking requirements unclear. | Retain if exactly one is intended, otherwise use scoped promotion links and immutable order-discount snapshots. | Business decision |
| `Product_Brand` | Many-to-many brands may be unnecessary for electronics products. | Confirm one manufacturer per product; if so, use Product.brand_id. Retain join table only if multi-brand items are intended. | Business decision |
| `Category.parent` / `Product_Category` | Hierarchy cycle and link uniqueness policies are not fully specified. | Use parent_id, forbid self-parent, validate cycles in service; maintain composite link uniqueness and FK indexes. | Before catalog writes |
| `Product_Image.is_primary` | Multiple primaries possible unless constrained. | Add partial UNIQUE(product_id) WHERE is_primary; add display_order if ordered galleries are required. | Before catalog writes |
| `Product_Specification` | Duplicate names/ordering not specified. | Define UNIQUE(product_id, specification_name) where appropriate and a display ordering policy. | Before catalog writes |
| `Brand`, `Category`, products | Slugs and lifecycle/uniqueness behavior are unspecified. | Decide whether public slugs, archival timestamps, and case-insensitive naming uniqueness are needed; avoid inventing unnecessary fields. | Catalog decision |
| `Event_Log.occured_at` | Misspelled; session_id is ambiguous. | Rename occurred_at; clarify analytics session versus authenticated session; allow anonymous events intentionally. Index by occurred_at/user/product as needed. | Analytics milestone |
| All tables | Some PK/FK markers exist, but full SQL constraints, nullability, defaults, indexes and deletion actions are not established by the diagram. | Write a data dictionary; index high-use FKs; define CHECK/unique/delete policies and optimistic concurrency where competing edits exist. Preserve orders/security history rather than cascading deletion by default. | Per migration |

Authentication implementation only needs the user/session/token subset first. These commerce issues should be resolved before their corresponding tables and services are implemented; they do not justify creating every domain table up front.

## Exact ERD field inventory

The following is transcribed from the ERD, preserving spelling and declared types. Empty markers/types mean the diagram did not provide them in that row.

### User

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `UUID` |
| `—` | `username` | `VARCHAR(255)` |
| `—` | `email` | `VARCHAR(255)` |
| `—` | `password` | `VARCHAR(255)` |
| `—` | `full_name` | `VARCHAR(100)` |
| `—` | `phone` | `VARCHAR(10)` |
| `—` | `email_verified` | `BOOLEAN` |
| `—` | `image_url` | `TEXT` |
| `—` | `role` | `VARCHAR(255)` |
| `—` | `is_active` | `BOOLEAN` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### User_Adress

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `user_id` | `UUID` |
| `—` | `province_city` | `VARCHAR(255)` |
| `—` | `ward_commune` | `VARCHAR(255)` |
| `—` | `street` | `VARCHAR(255)` |
| `—` | `is_default` | `BOOLEAN` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Auth_Provider

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `user_id` | `UUID` |
| `—` | `provider` | `VARCHAR(255)` |
| `—` | `provider_user_id` | `VARCHAR(255)` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Product

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `promotion_id` | `UUID` |
| `—` | `name` | `VARCHAR(255)` |
| `—` | `description` | `TEXT` |
| `—` | `avg_rating` | `NUMERIC(1, 1)` |
| `—` | `sold_count` | `INT` |
| `—` | `is_published` | `BOOLEAN` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Product_Image

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `product_id` | `INT` |
| `—` | `image_url` | `TEXT` |
| `—` | `is_primary` | `BOOLEAN` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Product_Specification

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `product_id` | `INT` |
| `—` | `specification_name` | `VARCHAR(255)` |
| `—` | `specification_value` | `TEXT` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Brand

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `—` | `name` | `VARCHAR(255)` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Product_Brand

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `(product_id, brand_id)` | `—` |
| `FK` | `product_id` | `INT` |
| `FK` | `brand_id` | `INT` |

### Category

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `parent` | `INT` |
| `—` | `name` | `VARCHAR(255)` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Product_Category

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `(product_id, cat_id)` | `—` |
| `FK` | `product_id` | `INT` |
| `FK` | `cat_id` | `INT` |

### Cart_Item

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `(user_id, product_id)` | `—` |
| `FK` | `user_id` | `UUID` |
| `FK` | `product_id` | `INT` |
| `FK` | `SKU` | `VARCHAR(32)` |
| `—` | `quantity` | `INT` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Review

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `product_id` | `INT` |
| `FK` | `SKU` | `VARCHAR(32)` |

### Order

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `order_code` | `UUID` |
| `FK` | `user_id` | `UUID` |
| `FK` | `voucher_id` | `UUID` |
| `FK` | `user_address_id` | `UUID` |
| `—` | `total_amount` | `NUMERIC(18, 2)` |
| `—` | `sub_total` | `NUMERIC(18, 2)` |
| `—` | `shipping_free` | `INT` |
| `—` | `discount_amount` | `NUMERIC(18, 2)` |
| `—` | `status` | `VARCHAR(255)` |
| `—` | `note` | `TEXT` |
| `—` | `payment_method` | `VARCHAR(255)` |
| `—` | `payment_amount` | `NUMERIC(18, 2)` |
| `—` | `paymemt_status` | `VARCHAR(255)` |
| `—` | `payment_description` | `TEXT` |
| `—` | `payment_expired_at` | `TIMESTAMPTZ` |

### Order_History

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `order_id` | `UUID` |
| `FK` | `updated_by` | `UUID` |
| `—` | `order_status` | `VARCHAR(255)` |
| `—` | `timestampt` | `TIMESTAMPTZ` |

### Voucher

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `UUID` |
| `—` | `code` | `VARCHAR(20)` |
| `—` | `applied_for` | `VARCHAR(255)` |
| `—` | `type` | `VARCHAR(255)` |
| `—` | `value` | `INT` |
| `—` | `is_active` | `BOOLEAN` |
| `—` | `start_date` | `TIMESTAMPTZ` |
| `—` | `end_date` | `TIMESTAMPTZ` |
| `—` | `quantity` | `INT` |
| `—` | `max_uses_per_user` | `INT` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### User_Voucher

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `(user_id, voucher_id)` | `—` |
| `FK` | `user_id` | `UUID` |
| `FK` | `voucher_id` | `UUID` |

### Promotion

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `UUID` |
| `—` | `code` | `VARCHAR(20)` |
| `—` | `applied_for` | `VARCHAR(255)` |
| `—` | `type` | `VARCHAR(255)` |
| `—` | `value` | `INT` |
| `—` | `is_active` | `BOOLEAN` |
| `—` | `start_date` | `TIMESTAMPTZ` |
| `—` | `end_date` | `TIMESTAMPTZ` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Order_Item

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `(order_code, product_id)` | `UUID` |
| `FK` | `order_code` | `UUID` |
| `FK` | `product_id` | `INT` |
| `FK` | `SKU` | `VARCHAR(16)` |
| `—` | `quantity` | `INT` |
| `—` | `calculated_price` | `NUMERIC(18, 2)` |
| `—` | `warranty_expired_at` | `TIMESTAMPTZ` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Aftersale_Request

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `product_id` | `INT` |
| `FK` | `SKU` | `VARCHAR(32)` |
| `—` | `type` | `VARCHAR(255)` |
| `—` | `reason` | `TEXT` |
| `—` | `description` | `TEXT` |
| `—` | `images` | `TEXT` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Aftersale_Request_History

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `request_id` | `INT` |
| `FK` | `user_id` | `UUID` |
| `—` | `request_status` | `VARCHAR(255)` |
| `—` | `timestampt` | `TIMESTAMPTZ` |

### Transaction_Log

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `order_code` | `UUID` |
| `—` | `status` | `VARCHAR(255)` |
| `—` | `raw_body` | `JSONB` |
| `—` | `request_header` | `TEXT` |
| `—` | `error` | `TEXT` |
| `—` | `attempt_count` | `INT` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Product_Variant

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `product_id` | `INT` |
| `U` | `SKU` | `VARCHAR(16)` |
| `—` | `price` | `INT` |
| `—` | `quantity` | `INT` |
| `—` | `created_at` | `TIMESTAMPTZ` |
| `—` | `updated_at` | `TIMESTAMPTZ` |

### Review_History

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `SERIAL` |
| `FK` | `user_id` | `UUID` |
| `FK` | `review_id` | `INT` |
| `—` | `rating` | `INT` |
| `—` | `comment` | `TEXT` |
| `—` | `created_at` | `TIMESTAMPTZ` |

### Event_Log

| Marker | Attribute | Declared type |
| --- | --- | --- |
| `PK` | `id` | `UUID` |
| `—` | `user_id` | `UUID` |
| `—` | `session_id` | `UUID` |
| `—` | `product_id` | `INT` |
| `—` | `event_type` | `VARCHAR(100)` |
| `—` | `rating` | `INT` |
| `—` | `quantity` | `INT` |
| `—` | `occured_at` | `TIMESTAMPTZ` |
| `—` | `received_at` | `TIMESTAMPTZ` |
