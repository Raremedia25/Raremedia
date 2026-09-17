-- =====================================================================================
-- V6: paid / not paid on every sale (with the customer's name for what is still owed), and the
--     settings behind e-mailed reports (recipient, daily schedule, SMTP account).
-- =====================================================================================

ALTER TABLE sales
    ADD COLUMN paid          BOOLEAN     NOT NULL DEFAULT TRUE,
    ADD COLUMN paid_at       TIMESTAMPTZ,
    ADD COLUMN customer_name TEXT;

-- everything recorded before this version was cash on the spot
UPDATE sales SET paid_at = sold_at WHERE paid = TRUE AND paid_at IS NULL;

CREATE INDEX ix_sales_unpaid ON sales (sold_at DESC) WHERE paid = FALSE;

INSERT INTO settings (key, value, value_type, category, description, critical) VALUES
 ('report.email',         '',      'STRING',  'report', 'Where reports are e-mailed (one address, or several separated by commas)', FALSE),
 ('report.daily_enabled', 'false', 'BOOLEAN', 'report', 'Send the day''s sales report by e-mail every day', FALSE),
 ('report.daily_time',    '20:00', 'STRING',  'report', 'Time of day (shop time) at which the daily report is sent', FALSE),
 ('report.last_sent_date','',      'STRING',  'report', 'Date the daily report was last sent (prevents duplicates)', FALSE),
 ('mail.host',            '',      'STRING',  'mail',   'SMTP server, e.g. smtp.gmail.com', FALSE),
 ('mail.port',            '587',   'INTEGER', 'mail',   'SMTP port (587 STARTTLS, 465 SSL)', FALSE),
 ('mail.username',        '',      'STRING',  'mail',   'SMTP login (usually the sending e-mail address)', FALSE),
 ('mail.password',        '',      'STRING',  'mail',   'SMTP password or app password', FALSE),
 ('mail.from',            '',      'STRING',  'mail',   'Sender address shown on the e-mail (defaults to the username)', FALSE)
ON CONFLICT (key) DO NOTHING;
