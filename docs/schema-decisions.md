# ERD implementation decisions

Source: NUTEE_EERD_ERD.drawio, ERD page `i9jcEZAXrc9SuSMo_mB8`, supplied by the user.
The user authorized schema implementation and local/Google authentication.

V1 creates all 24 ERD tables. SQL names use lowercase snake_case and plural table
names (`User` -> `users`, `Order` -> `orders`, `User_Adress` -> `user_addresses`).
Columns retain their source meaning with these spelling/type corrections:

* `Order.user_address_id`: INTEGER to reference `User_Adress.id` (SERIAL).
* `Product.avg_rating`: NUMERIC(2,1), since NUMERIC(1,1) cannot hold 5.0.
* All SKU columns: VARCHAR(16), matching the unique Product_Variant.SKU.
* `shipping_free` -> `shipping_fee`; `paymemt_status` -> `payment_status`.
* `timestampt` and `occured_at` -> `occurred_at`.
* Product variant references use `(product_id, sku)` to prevent mismatched pairs.
* Cart and order item primary keys remain `(user_id, product_id)` and
  `(order_code, product_id)`. They allow one variant per product in a cart/order.
* Event_Log attributes remain attributes; no extra event foreign keys were inferred.
  Aftersale_Request has no order FK in the source, so none is invented.
* The source does not specify nullability/defaults for most non-key attributes.
  Mandatory relationship FKs are NOT NULL; optional promotion/address/voucher
  references are nullable. Other non-authentication attributes remain nullable.
  Created/updated timestamps default to now; no update triggers are added.
* Authentication requires case-insensitive unique usernames/emails, unique provider
  identities, and one identity per provider per user. Google-only users have NULL
  passwords. Passwords are BCrypt hashes; the default role is CUSTOMER.

Per the updated user request, V2 and `refresh_sessions` have been removed. V1 adds
only `users.refresh_token` (nullable TEXT) for the current signed refresh JWT.
User ID, email, and expiry are JWT claims; no separate token metadata is persisted.
Each login replaces the stored token, refresh rotates it under a user row lock,
and logout clears it when the supplied token matches. Keys are not stored in DB.

Only user/provider tables need JPA mappings for this delivery. Other domain
tables are migrated and constrained; domain CRUD remains outside this auth task.
