# Cart API

The cart is stored in PostgreSQL for the active account from the access JWT.
All routes use `/api/v1`, HttpOnly authentication cookies, `Cache-Control: no-store`
and the existing CSRF protection for writes. Fetch the CSRF token from
`GET /auth/csrf` and send its named header with every mutation.
Guests must sign in; there is no browser-stored cart or guest merge.

| Method / path | Request | Behavior |
| --- | --- | --- |
| GET `/cart` | — | Read the current account's cart |
| POST `/cart/items` | `{ "variantId": 12, "quantity": 2 }` | Add to the existing variant quantity, or create its row |
| PUT `/cart/items/{variantId}` | `{ "quantity": 3 }` | Set the absolute quantity of an existing row |
| DELETE `/cart/items/{variantId}` | — | Remove that variant; repeating the request is safe |
| DELETE `/cart` | — | Clear only the current account's cart |

Every successful request returns HTTP 200 and the current `CartResponse`:

```json
{
    "ownerId": "00000000-0000-0000-0000-000000000001",
    "items": [{
        "variantId": 12,
        "productId": 1,
        "sku": "TEST-LAPTOP-16",
        "name": "Test laptop",
        "imageUrl": "/test-laptop.svg",
        "price": 12000000,
        "quantity": 2,
        "stock": 3,
        "status": "available",
        "lineTotal": 24000000,
        "specifications": [{ "name": "ram", "value": "16 GB" }]
    }],
    "quantity": 2,
    "subtotal": 24000000,
    "hasIssues": false
}
```

`quantity` counts units across all rows. `subtotal` includes only rows with
`status: available`; the frontend may calculate a smaller total for selected rows.
Prices, SKU, image, specifications and stock come from the current variant/product,
never from client prices or the legacy cart SKU column. Unknown prices and line
totals remain null. Variant images take priority over the product's primary image.

Quantity is an integer from 1 to 99. The combined quantity after an add, or the
absolute quantity after a PUT, must not exceed current stock. A product must be
published and its variant must have a known nonnegative price and stock.
Validation errors return 400; missing variants/rows return 404; unavailable
variants or quantities beyond stock/the per-variant limit return 409.
Unauthenticated/inactive accounts return 401; invalid CSRF returns 403.

Existing rows remain visible when product availability changes:

- `hidden`: unpublished product or unknown price/stock.
- `out-of-stock`: no current stock.
- `insufficient-stock`: reduce quantity to fit current stock or the limit.
- `available`: eligible for selection.

Unavailable rows can always be removed. Different variants of the same product
remain independent. No stock is reserved or deducted by cart operations, so
checkout must revalidate stock and prices when it is implemented.

Writes acquire a pessimistic lock on the account before accessing cart rows.
This also protects concurrent first inserts, where no cart row exists to lock.
The existing UNIQUE `(user_id, variant_id)` remains the database safeguard.
Concurrent adds accumulate without a lost update. PUT is absolute and the last
serialized write wins; it is not optimistic versioning. POST is additive and
should not be retried after an ambiguous network failure without checking the cart.
All row lookups/deletes include the JWT account ID. Client `userId`/prices cannot
change ownership or pricing.

Spring Data JPA uses derived queries and an EntityGraph for cart/variant/product
reads, with batched image/specification reads to avoid N+1. The existing schema
and three applied Flyway migrations are unchanged. `CartIT` covers HTTP contracts,
ownership/CSRF, stock changes, quantity limits, bounded query counts and real
concurrent writes on isolated PostgreSQL.
