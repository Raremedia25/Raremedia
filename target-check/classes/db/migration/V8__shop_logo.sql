-- =====================================================================================
-- V8: the shop's logo (one picture), shown in the app, on receipts and on the PDF report.
-- =====================================================================================

CREATE TABLE shop_logo (
    id            SMALLINT    PRIMARY KEY CHECK (id = 1),   -- exactly one row
    content       BYTEA       NOT NULL,
    content_type  TEXT        NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
