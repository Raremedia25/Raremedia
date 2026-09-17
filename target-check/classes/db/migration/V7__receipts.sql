-- =====================================================================================
-- V7: several items on one receipt. Each line stays one row in sales; the lines of one sale share a
--     receipt number drawn from receipt_seq. Existing single-line sales keep their id as receipt number.
-- =====================================================================================

ALTER TABLE sales ADD COLUMN receipt_no BIGINT;
UPDATE sales SET receipt_no = id WHERE receipt_no IS NULL;
ALTER TABLE sales ALTER COLUMN receipt_no SET NOT NULL;
CREATE INDEX ix_sales_receipt ON sales (receipt_no);

CREATE SEQUENCE receipt_seq;
SELECT setval('receipt_seq', COALESCE((SELECT MAX(receipt_no) FROM sales), 0) + 1, false);
