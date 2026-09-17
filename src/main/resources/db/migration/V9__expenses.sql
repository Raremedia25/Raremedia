-- =====================================================================================
-- V9: expenses — money the shop spends (rent, transport, airtime, stock purchases…), so the report can
--     show sales minus expenses. One row per expense; the date is the day it was spent, not the day typed.
-- =====================================================================================

CREATE TABLE expenses (
    id           BIGSERIAL     PRIMARY KEY,
    spent_on     DATE          NOT NULL,
    category     TEXT          NOT NULL,
    description  TEXT          NOT NULL,
    amount       NUMERIC(14,2) NOT NULL CHECK (amount > 0),
    note         TEXT,
    recorded_by  BIGINT        REFERENCES users (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_expenses_spent_on ON expenses (spent_on DESC, id DESC);
CREATE INDEX ix_expenses_category ON expenses (category);
