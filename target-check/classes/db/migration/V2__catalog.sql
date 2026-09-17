-- =====================================================================================
-- V2: catalog — categories, brands, products.
-- Money: NUMERIC(14,2) amounts. Soft delete with partial unique indexes so a deleted product's
-- SKU/barcode can be reused. supplier_id is added by V4 (partners) once the suppliers table exists.
-- =====================================================================================

CREATE TABLE categories (
    id           BIGSERIAL PRIMARY KEY,
    name         TEXT        NOT NULL,
    description  TEXT,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    deleted_at   TIMESTAMPTZ,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT      REFERENCES users (id),
    updated_by   BIGINT      REFERENCES users (id)
);
CREATE UNIQUE INDEX ux_categories_name ON categories (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE brands (
    id           BIGSERIAL PRIMARY KEY,
    name         TEXT        NOT NULL,
    description  TEXT,
    active       BOOLEAN     NOT NULL DEFAULT TRUE,
    deleted_at   TIMESTAMPTZ,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by   BIGINT      REFERENCES users (id),
    updated_by   BIGINT      REFERENCES users (id)
);
CREATE UNIQUE INDEX ux_brands_name ON brands (lower(name)) WHERE deleted_at IS NULL;

CREATE TABLE products (
    id               BIGSERIAL PRIMARY KEY,
    sku              TEXT        NOT NULL,
    barcode          TEXT,
    name             TEXT        NOT NULL,
    category_id      BIGINT      NOT NULL REFERENCES categories (id) ON DELETE RESTRICT,
    brand_id         BIGINT      REFERENCES brands (id) ON DELETE RESTRICT,
    model            TEXT,
    description      TEXT,
    unit             TEXT        NOT NULL DEFAULT 'PCS',
    purchase_price   NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK (purchase_price  >= 0),  -- reference cost; COGS uses inventory.avg_cost
    retail_price     NUMERIC(14,2) NOT NULL CHECK (retail_price    >= 0),
    wholesale_price  NUMERIC(14,2) NOT NULL CHECK (wholesale_price >= 0),
    discount_price   NUMERIC(14,2) CHECK (discount_price >= 0),
    min_stock        INT         NOT NULL DEFAULT 0 CHECK (min_stock >= 0),
    max_stock        INT         CHECK (max_stock IS NULL OR max_stock >= min_stock),
    warranty_months  INT         CHECK (warranty_months IS NULL OR warranty_months >= 0),
    requires_serial  BOOLEAN     NOT NULL DEFAULT FALSE,
    image_path       TEXT,
    active           BOOLEAN     NOT NULL DEFAULT TRUE,
    deleted_at       TIMESTAMPTZ,
    version          BIGINT      NOT NULL DEFAULT 0,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by       BIGINT      REFERENCES users (id),
    updated_by       BIGINT      REFERENCES users (id)
);
CREATE UNIQUE INDEX ux_products_sku     ON products (lower(sku))     WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_products_barcode ON products (barcode)        WHERE deleted_at IS NULL AND barcode IS NOT NULL;
CREATE INDEX ix_products_name     ON products (lower(name));
CREATE INDEX ix_products_category ON products (category_id);
CREATE INDEX ix_products_brand    ON products (brand_id);
CREATE INDEX ix_products_active   ON products (active, deleted_at);

-- Starter categories and brands typical for an electronics shop (edit freely in the UI).
INSERT INTO categories (name, description) VALUES
 ('Phones',               'Smartphones and feature phones'),
 ('Chargers & Cables',    'Wall chargers, car chargers, USB cables'),
 ('Power Banks',          'Portable batteries'),
 ('Audio',                'Earphones, headphones, speakers'),
 ('Storage',              'Memory cards, flash drives, hard disks'),
 ('Computer Accessories', 'Mice, keyboards, adapters, laptop bags'),
 ('Screen Protectors & Cases', 'Protective accessories');

INSERT INTO brands (name) VALUES
 ('Samsung'), ('Tecno'), ('Infinix'), ('Itel'), ('Apple'), ('Xiaomi'), ('Oraimo'), ('Anker'),
 ('SanDisk'), ('Kingston'), ('JBL'), ('Generic');
