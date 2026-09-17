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
| Sell Items | Tap products to build one receipt with **several items**, adjust quantities, see the total, press SELL. **Paid** or **not paid** (credit: the customer's name is required). Overselling of any item refuses the whole receipt. Prints a **receipt**. Phone-friendly |
| Receipt | Printable ticket per sale: receipt number, date, all items, total, PAID / NOT PAID stamp, customer (`receipt.html?no=000123`) |
| Sales History | Every sale with date filters (today / week / month / custom), paid / not paid filter, search by product or customer, totals and amount still owed; mark a sale paid; open its receipt |
| Stock | Stock · Sold · Available per product with AVAILABLE / LOW STOCK / OUT OF STOCK badges; "+ Add Stock" |
| Reports | Sales per product for a period (sold, sales, not paid, remaining stock) plus paid / not paid totals and the list of who still owes; **Download PDF** (A4, with the shop logo), printable, **E-mail report** button |
| Expenses | Admin records what the shop spends (date, category, description, amount); filters by period and category with totals; the report shows **sales minus expenses** |
| Workers | The admin adds worker accounts (name, username, first password), disables, resets or removes them |
| Settings | Shop name and contact details, **shop logo** (sidebar, login page, receipts, PDF), low-stock level (default 5), change password; **E-mail reports**: your address, a daily report at a set time, and the mail account (SMTP) that sends it |

### E-mailed reports

In **Settings → E-mail reports** the administrator enters the address the reports go to, ticks "Send the day's
report automatically every day" and picks the time (shop time, default 20:00), and fills in the mail account the
server sends from (for Gmail: `smtp.gmail.com`, port `587`, your Gmail address, and an *App password*).
"Send test e-mail" checks the account; "Send today's report now" and the **E-mail report** button on the Reports
page send a report on demand. The daily report is sent once per day at or after the set time while the server is
running (if the server was off at that time and comes back the same day, it is sent then). Each e-mail contains
items sold, total sales, paid / not paid, the per-product table, the list of unpaid sales with customer names, and
the low-stock list.

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
sales       id, receipt_no, product_id, product_name, quantity, unit_price, total, sold_at, sold_by,
            paid, paid_at, customer_name          -- one row per item; items sold together share receipt_no (sequence receipt_seq)
categories  id, name
settings    key, value                 (company.name, stock.low_threshold, report.email, report.daily_*, mail.*, …)
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
GET    /api/sales?from&to&q&paid&page&size&sort   GET /api/sales/{id}   GET /api/sales/receipt/{no}
POST   /api/sales  { "items": [ { "productId": 5, "quantity": 3 }, … ], "paid": false, "customerName": "Uwase" }
       (or a single "productId"/"quantity"; paid defaults to true; answers with the receipt and its lines)
POST   /api/sales/receipt/{no}/paid  { "paid": true }     POST /api/sales/{id}/paid  (pays the receipt the line belongs to)
GET    /api/dashboard
GET    /api/reports/sales?from&to            GET /api/reports/sales/pdf?from&to (download)   POST /api/reports/sales/email?from&to   (admin)
GET/PUT /api/settings                        GET/PUT /api/settings/mail   POST /api/settings/mail/test   (admin)
GET    /api/settings/logo (public image)     POST /api/settings/logo (multipart "file", jpeg/png/gif ≤ 1 MB)   DELETE /api/settings/logo   (admin)
GET    /api/expenses?from&to&category&q&page&size&sort   GET /api/expenses/categories   POST/PUT/DELETE /api/expenses[/{id}]   (admin)
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
