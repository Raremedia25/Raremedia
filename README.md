# THEO TECH LTD — Sales & Stock Tracker

A small, fast application for an electronics shop. It answers three questions:

1. **What items do we have?** — products with a category and a price.
2. **How many have we sold?** — every sale is recorded with quantity and total.
3. **How many are left?** — `available = stock put on the shelf − sold`, computed live, never stored.

**Stack:** Java 25 · Spring Boot 4.1 (Spring MVC, Data JPA, Security, Flyway) · PostgreSQL 18 ·
Bootstrap 5.3 + vanilla JavaScript (no build step, no CDN — works offline on a LAN).

## What it does

| Page | Purpose |
|---|---|
| Dashboard | Total products, items in stock, items sold, total sales; low-stock and out-of-stock lists; recent sales |
| Products | Add / edit / delete / search products; a picture per product; add stock; manage the category list |
| Sell Item | Pick a product and a quantity, see the total, press SELL. Overselling is refused. Phone-friendly |
| Sales History | Every sale with date filters (today / week / month / custom), search and totals |
| Stock | Stock · Sold · Available per product with AVAILABLE / LOW STOCK / OUT OF STOCK badges; "+ Add Stock" |
| Reports | Sales per product for a period (sold, sales, remaining stock), printable |
| Workers | The admin adds worker accounts (name, username, first password), disables, resets or removes them |
| Settings | Shop name and contact details, low-stock level (default 5), change password |

Two kinds of user: the **administrator** can do everything; **workers** can sign in, sell and look at products,
stock and sales history. Nothing else: no customers, suppliers, purchases, returns or audit log.

## Repository layout

```
mvnw.cmd / mvnw          Maven wrapper (downloads Maven itself; no Maven install needed)
pom.xml
scripts\                 db-init / db-start / db-stop / db-status / db-psql / build / run
database\schema.sql      reference dump of the schema (Flyway migrations are the source of truth)
docker-compose.yml       PostgreSQL + app for deployment on another machine
src\main\java\com\theotech
  common\                envelope, exceptions, base entities, paging
  config\                properties, JPA auditing
  security\              login, session, CSRF, lockout, password change
  iam\                   the users table behind the login; worker accounts (/api/workers)
  settings\              shop name + low-stock level        (/api/settings)
  catalog\               categories and products, add stock (/api/categories, /api/products)
  sales\                 selling and sales history          (/api/sales)
  dashboard\             the four figures and the lists     (/api/dashboard)
  reports\               sales per product for a period     (/api/reports/sales)
src\main\resources
  application*.yml
  db\migration\          Flyway migrations V1..V5 (ddl-auto=validate: Hibernate never alters the schema)
  static\                the frontend: index / products / sell / sales / stock / reports / workers / settings + css/ js/ vendor/
  demo\                  the twelve sample product pictures (SVG) inserted on first start in the dev profile
.removed-phase3\         the earlier, larger design (inventory ledger, audit, roles, brands…) kept for reference; not compiled
```

## Database (the tables that matter)

```
products    id, name, category_id, price, initial_stock, sold_quantity, image_updated_at, created_at, updated_at, deleted_at
product_images  product_id, content (bytea), content_type   -- one picture per product, max 2 MB
sales       id, product_id, product_name, quantity, unit_price, total, sold_at, sold_by
categories  id, name
settings    key, value                 (company.name, stock.low_threshold, …)
users       the administrator and the workers (role SALES_STAFF = worker)
```

`initial_stock` is everything ever put on the shelf (opening stock + every "Add stock");
`available = initial_stock − sold_quantity`. A check constraint keeps `sold_quantity ≤ initial_stock`,
and a sale locks the product row, so stock can never go negative.

## REST API

```
GET    /api/products?q=&categoryId=          GET/PUT/DELETE /api/products/{id}     POST /api/products
POST   /api/products/{id}/stock              { "quantity": 20 }
GET/POST/DELETE /api/products/{id}/image     the picture (POST = multipart "file", jpeg/png/webp/gif)
GET/POST /api/workers   PUT /api/workers/{id}   POST /api/workers/{id}/reset-password   DELETE /api/workers/{id}   (admin only)
GET    /api/categories                       POST /api/categories                  DELETE /api/categories/{id}
GET    /api/sales?from&to&q&page&size&sort   GET /api/sales/{id}                   POST /api/sales  { "productId": 5, "quantity": 3 }
GET    /api/dashboard
GET    /api/reports/sales?from&to
GET/PUT /api/settings
POST   /api/auth/login (form)   POST /api/auth/logout   GET /api/auth/me   POST /api/auth/change-password
```

Responses use one envelope: `{ "success": true, "data": … }` or
`{ "success": false, "code": "INSUFFICIENT_STOCK", "errors": { "available": "5", "requested": "8" } }`.

## Running locally on Windows (no admin rights)

Everything lives under the project folder; nothing is installed system-wide.

1. **Database (first time only):** unzip the PostgreSQL 18 Windows binaries into `.tooling\pgsql`
   (so that `.tooling\pgsql\bin\pg_ctl.exe` exists), then run `scripts\db-init.cmd`.
2. **Start / stop the database:** `scripts\db-start.cmd`, `scripts\db-stop.cmd`, `scripts\db-status.cmd`.
3. **Run the application:** `scripts\run.cmd` → <http://localhost:8080>

   First login: **admin / Admin@123** — you are asked to set a new password immediately.
   In the dev profile the first start also inserts twelve sample products with pictures (once; delete them freely).
4. **Build & test:** `scripts\build.cmd` (= `clean verify`). Integration tests run against the real
   `theo_tech_test` database when the cluster is up and are skipped when it is not.

Environment overrides: `APP_DB_PASSWORD` (default `theotech`), `APP_REMEMBER_ME_KEY`.

## Running with Docker (other machines)

    APP_DB_PASSWORD=strong-password docker compose up -d --build
