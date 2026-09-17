-- =====================================================================================
-- V3: inventory — current stock per product per branch, and the immutable stock_movements ledger.
-- Every stock change is a movement row carrying balance_after and avg_cost_after, so the ledger
-- alone can reconstruct stock and cost at any point in time.
-- =====================================================================================

CREATE TABLE inventory (
    id          BIGSERIAL PRIMARY KEY,
    product_id  BIGINT        NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    branch_id   BIGINT        NOT NULL REFERENCES branches (id) ON DELETE RESTRICT,
    quantity    INT           NOT NULL DEFAULT 0,
    avg_cost    NUMERIC(16,4) NOT NULL DEFAULT 0 CHECK (avg_cost >= 0),   -- moving weighted average
    version     BIGINT        NOT NULL DEFAULT 0,
    updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
    UNIQUE (product_id, branch_id)
);
CREATE INDEX ix_inventory_branch ON inventory (branch_id, quantity);

CREATE TABLE stock_movements (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT        NOT NULL REFERENCES products (id) ON DELETE RESTRICT,
    branch_id       BIGINT        NOT NULL REFERENCES branches (id) ON DELETE RESTRICT,
    type            TEXT          NOT NULL CHECK (type IN ('OPENING','PURCHASE','SALE','SALE_RETURN',
                                                          'PURCHASE_RETURN','ADJUSTMENT','DAMAGE',
                                                          'TRANSFER_IN','TRANSFER_OUT')),
    quantity_delta  INT           NOT NULL,                 -- signed
    balance_after   INT           NOT NULL,                 -- running balance for this product+branch
    unit_cost       NUMERIC(16,4),                          -- cost of the units moved (inbound: acquisition cost; outbound: avg cost)
    avg_cost_after  NUMERIC(16,4) NOT NULL,
    reference_type  TEXT,                                   -- SALE, PURCHASE, RETURN, ADJUSTMENT, ...
    reference_id    BIGINT,
    note            TEXT,
    user_id         BIGINT        NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_stock_movements_product   ON stock_movements (product_id, created_at DESC);
CREATE INDEX ix_stock_movements_reference ON stock_movements (reference_type, reference_id);
CREATE INDEX ix_stock_movements_branch    ON stock_movements (branch_id, created_at DESC);
