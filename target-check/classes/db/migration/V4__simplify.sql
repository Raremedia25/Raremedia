-- =====================================================================================
-- V4: simplify THEO TECH to a stock & sales tracker.
--   * products keep only name, category, price and two counters: initial_stock (everything ever put on the
--     shelf, opening stock + later additions) and sold_quantity. Available stock is never stored:
--     available = initial_stock - sold_quantity.
--   * one sales table: who sold what, how many, at which price, when.
--   * the inventory ledger, audit log, brands and branch/role machinery of the earlier design are dropped.
-- =====================================================================================

DROP TABLE IF EXISTS stock_movements;
DROP TABLE IF EXISTS inventory;
DROP TABLE IF EXISTS audit_logs;

-- ---------- products -------------------------------------------------------------------
ALTER TABLE products
    DROP COLUMN sku,
    DROP COLUMN barcode,
    DROP COLUMN brand_id,
    DROP COLUMN model,
    DROP COLUMN description,
    DROP COLUMN unit,
    DROP COLUMN purchase_price,
    DROP COLUMN wholesale_price,
    DROP COLUMN discount_price,
    DROP COLUMN min_stock,
    DROP COLUMN max_stock,
    DROP COLUMN warranty_months,
    DROP COLUMN requires_serial,
    DROP COLUMN image_path,
    DROP COLUMN active;

ALTER TABLE products RENAME COLUMN retail_price TO price;

ALTER TABLE products
    ADD COLUMN initial_stock INT NOT NULL DEFAULT 0 CHECK (initial_stock >= 0),
    ADD COLUMN sold_quantity INT NOT NULL DEFAULT 0 CHECK (sold_quantity >= 0),
    ADD CONSTRAINT ck_products_not_oversold CHECK (sold_quantity <= initial_stock);

DROP INDEX IF EXISTS ix_products_name;
CREATE UNIQUE INDEX ux_products_name ON products (lower(name)) WHERE deleted_at IS NULL;

DROP TABLE IF EXISTS brands;

-- ---------- sales ----------------------------------------------------------------------
CREATE TABLE sales (
    id            BIGSERIAL     PRIMARY KEY,
    product_id    BIGINT        NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    product_name  TEXT          NOT NULL,                        -- snapshot, survives renames/deletion
    quantity      INT           NOT NULL CHECK (quantity > 0),
    unit_price    NUMERIC(14,2) NOT NULL CHECK (unit_price >= 0), -- product price at the time of sale
    total         NUMERIC(14,2) NOT NULL CHECK (total >= 0),      -- unit_price * quantity
    sold_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    sold_by       BIGINT        REFERENCES users (id)
);
CREATE INDEX ix_sales_sold_at ON sales (sold_at DESC, id DESC);
CREATE INDEX ix_sales_product ON sales (product_id);

-- ---------- categories: the simple list for an electronics shop ------------------------
INSERT INTO categories (name)
SELECT v.name
FROM (VALUES ('Phones'), ('Chargers'), ('Cables'), ('Batteries'), ('Bluetooth'), ('Earphones'), ('Headphones'),
             ('Radios'), ('Speakers'), ('Power Banks'), ('Memory Cards'), ('Computer Accessories'), ('Other')) AS v(name)
WHERE NOT EXISTS (SELECT 1 FROM categories c WHERE lower(c.name) = lower(v.name) AND c.deleted_at IS NULL);

-- retire the earlier seed names that nothing uses
UPDATE categories
SET deleted_at = now(), active = FALSE
WHERE deleted_at IS NULL
  AND name IN ('Chargers & Cables', 'Audio', 'Storage', 'Screen Protectors & Cases')
  AND NOT EXISTS (SELECT 1 FROM products p WHERE p.category_id = categories.id);

-- ---------- settings -------------------------------------------------------------------
INSERT INTO settings (key, value, value_type, category, description, critical)
VALUES ('stock.low_threshold', '5', 'INTEGER', 'stock', 'Products with this many units or fewer are shown as low stock', FALSE)
ON CONFLICT (key) DO NOTHING;
