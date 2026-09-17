-- =====================================================================================
-- V5: one picture per product, stored in the database (small shop: no upload folders to back up
-- separately). products.image_updated_at doubles as the "has image" flag and the cache-busting version.
-- =====================================================================================

ALTER TABLE products ADD COLUMN image_updated_at TIMESTAMPTZ;

CREATE TABLE product_images (
    product_id    BIGINT      PRIMARY KEY REFERENCES products (id) ON DELETE CASCADE,
    content       BYTEA       NOT NULL,
    content_type  TEXT        NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
