# THEO TECH LTD — working conventions

Spring Boot 4.1.1 · Java 25 · Spring Security 7 · Jackson 3 · Hibernate 7 · Flyway · PostgreSQL 18.
Read `README.md` for what the app does and how to run it. Build with `scripts\build.cmd` (never a system Maven).

## What this is (and is not)
A **simple stock & sales tracker** for one electronics shop: products, add stock, sell, see what is left,
sales history, one report, a few settings, a picture per product, one administrator plus worker accounts.
It is deliberately **not** an ERP: no customers, suppliers, purchases, returns, fine-grained permissions, audit log,
branches, i18n.
The earlier, larger design lives in `.removed-phase3\` for reference only — do not compile or revive it
unless asked. Prefer removing over adding.

## Non-negotiable rules
- **Boot 4 idioms only.** `spring-boot-starter-webmvc` (not `-web`). Jackson lives in `tools.jackson.*`
  (annotations stay in `com.fasterxml.jackson.annotation`). Security config is lambda DSL only.
  `@WebMvcTest` etc. need the matching `-test` starter (already in the POM).
- **Flyway owns the schema** (`ddl-auto=validate`). Schema changes = new `V<n>__*.sql`. Never edit an applied migration.
- **Stock arithmetic lives on the `Product` entity**: `available = initialStock - soldQuantity`, never stored.
  `Product.sell()` refuses to go negative (`INSUFFICIENT_STOCK`, HTTP 409); `SaleService.sell()` locks the row
  (`ProductRepository.lockActiveById`) and writes the `Sale` in the same transaction. The DB check
  `sold_quantity <= initial_stock` is the last line of defence.
- **Money**: `NUMERIC(14,2)` / `BigDecimal`, never `double`. A sale snapshots `unit_price` and `total`.
  One `sales` row per item; the items of one ticket share `receipt_no` (sequence `receipt_seq`, V7) and are
  sold in one transaction (`SaleService.sell` locks products in ascending id order). The only mutable thing on a
  line is `paid`/`paid_at`, always toggled for the whole receipt (`setReceiptPaid`); an unpaid sale needs
  `customer_name`. `Sale.receiptNo()` is the zero-padded number printed by `receipt.html?no=`.
- **E-mail** goes through `common.mail.EmailSender` (SMTP impl reads `mail.*` from settings on every send; mock it
  with `@MockitoBean` in ITs). Reports are rendered by `reports.service.ReportHtml`; `DailyReportScheduler` runs
  every minute and sends once per day at/after `report.daily_time` (guard: `report.last_sent_date`).
  `ReportService.build()` is unguarded on purpose (the scheduler has no user); `sales()` is the admin-only façade.
- **API envelope**: `ApiResponse.ok(data)`; errors are `AppException` subclasses with a stable `code`.
  Bean-validation messages are short keys (`required`, `min`, `positive`, `belowSold`…) that `js/ui.js` turns into text.
- **Two levels only.** Admin-only service methods carry `@PreAuthorize("hasRole('ADMIN')")` (product/category changes,
  add stock, pictures, settings update, workers, report). Everything else is open to any signed-in user (workers sell and look).
  Workers hold the seeded `SALES_STAFF` role; its permission rows are unused. Frontend: `Auth.isAdmin`, `data-admin` on
  elements/pages hides admin-only UI (the server still enforces it).
- **Frontend**: one static page per screen in `src/main/resources/static`, English text written directly in the
  HTML/JS, `<body data-page="x" data-title="Title">` + `<div id="app-content" hidden>` + the shared scripts
  (`api.js`, `ui.js`, `auth.js`, `layout.js`, optionally `datatable.js`). No CDN, no framework, no build step.
  Money via `UI.money()`, badges via `UI.stockBadge()`, pictures via `UI.thumb()`, forms via `UI.modal()`, uploads via `Api.upload()`.
  Static assets are served `no-cache`; app script/css links carry `?v=N` — bump N when a shared script changes shape.
- **Tests**: unit tests `*Test` (surefire), integration tests `*IT` (failsafe, real `theo_tech_test` DB,
  `@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional`). Log in with
  `TestAuth.loginAsAdmin(mvc, users)`; mutating requests go through `TestAuth.json(...)` / `withCsrf(...)`.
  Never assert bare numbers on JSON bodies (timestamps make it flaky); assert fields.

## Package layout (feature-first, layered inside)
`com.theotech.<feature>.{domain,repository,service,web,dto}` — features: `catalog` (categories, products),
`sales`, `dashboard`, `reports`, `settings`, `iam` (login + workers), plus `security`, `common`, `config`
(`config.DemoDataSeeder`: dev-profile sample products with the SVGs in `resources/demo`, runs once).
