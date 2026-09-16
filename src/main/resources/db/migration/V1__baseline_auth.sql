-- =====================================================================================
-- V1: baseline — branches, identity & access (users/roles/permissions), remember-me,
--     audit log, settings. Flyway owns the schema; Hibernate only validates against it.
-- =====================================================================================

-- ---------- branches -------------------------------------------------------------------
CREATE TABLE branches (
    id          BIGSERIAL PRIMARY KEY,
    code        TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    address     TEXT,
    phone       TEXT,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_branches_code ON branches (code);

-- ---------- users ----------------------------------------------------------------------
CREATE TABLE users (
    id                     BIGSERIAL PRIMARY KEY,
    username               TEXT        NOT NULL,
    email                  TEXT,
    full_name              TEXT        NOT NULL,
    phone                  TEXT,
    password_hash          TEXT        NOT NULL,
    enabled                BOOLEAN     NOT NULL DEFAULT TRUE,
    locked                 BOOLEAN     NOT NULL DEFAULT FALSE,   -- manual lock by an admin
    locked_until           TIMESTAMPTZ,                          -- automatic lock after failed logins
    failed_login_attempts  INT         NOT NULL DEFAULT 0 CHECK (failed_login_attempts >= 0),
    must_change_password   BOOLEAN     NOT NULL DEFAULT FALSE,
    password_changed_at    TIMESTAMPTZ,
    last_login_at          TIMESTAMPTZ,
    preferred_language     TEXT        NOT NULL DEFAULT 'en' CHECK (preferred_language IN ('en','rw')),
    default_branch_id      BIGINT      REFERENCES branches (id) ON DELETE RESTRICT,
    deleted_at             TIMESTAMPTZ,
    version                BIGINT      NOT NULL DEFAULT 0,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by             BIGINT      REFERENCES users (id),
    updated_by             BIGINT      REFERENCES users (id)
);
-- partial unique indexes: a deleted user's username/email can be reused
CREATE UNIQUE INDEX ux_users_username ON users (lower(username)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX ux_users_email    ON users (lower(email))    WHERE deleted_at IS NULL AND email IS NOT NULL;
CREATE INDEX ix_users_active ON users (enabled, deleted_at);

-- ---------- roles & permissions ---------------------------------------------------------
CREATE TABLE roles (
    id           BIGSERIAL PRIMARY KEY,
    name         TEXT        NOT NULL,          -- e.g. ADMIN (authority is ROLE_ADMIN)
    description  TEXT,
    system_role  BOOLEAN     NOT NULL DEFAULT FALSE,  -- seeded roles cannot be deleted
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_roles_name ON roles (name);

CREATE TABLE permissions (
    id           BIGSERIAL PRIMARY KEY,
    name         TEXT NOT NULL,                 -- e.g. SALE_CREATE
    module       TEXT NOT NULL,                 -- grouping for the role editor UI
    description  TEXT NOT NULL
);
CREATE UNIQUE INDEX ux_permissions_name ON permissions (name);

CREATE TABLE user_roles (
    user_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES roles (id) ON DELETE RESTRICT,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

CREATE TABLE role_permissions (
    role_id        BIGINT NOT NULL REFERENCES roles (id)       ON DELETE CASCADE,
    permission_id  BIGINT NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

-- ---------- remember-me (Spring Security PersistentTokenRepository schema) ---------------
CREATE TABLE persistent_logins (
    username   VARCHAR(64) NOT NULL,
    series     VARCHAR(64) PRIMARY KEY,
    token      VARCHAR(64) NOT NULL,
    last_used  TIMESTAMP   NOT NULL
);
CREATE INDEX ix_persistent_logins_username ON persistent_logins (username);

-- ---------- audit log (append-only) ------------------------------------------------------
CREATE TABLE audit_logs (
    id            BIGSERIAL PRIMARY KEY,
    occurred_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    user_id       BIGINT      REFERENCES users (id),   -- NULL for failed logins of unknown users
    username      TEXT,                                -- frozen copy
    action        TEXT        NOT NULL,                -- LOGIN_SUCCESS, LOGIN_FAILED, SALE_CREATED, ...
    entity_type   TEXT,
    entity_id     BIGINT,
    summary       TEXT,
    details       JSONB,
    ip_address    TEXT,
    user_agent    TEXT,
    branch_id     BIGINT      REFERENCES branches (id)
);
CREATE INDEX ix_audit_logs_occurred ON audit_logs (occurred_at DESC);
CREATE INDEX ix_audit_logs_user     ON audit_logs (user_id, occurred_at DESC);
CREATE INDEX ix_audit_logs_entity   ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_logs_action   ON audit_logs (action, occurred_at DESC);

-- ---------- settings (key/value) ---------------------------------------------------------
CREATE TABLE settings (
    key          TEXT        PRIMARY KEY,
    value        TEXT        NOT NULL,
    value_type   TEXT        NOT NULL DEFAULT 'STRING' CHECK (value_type IN ('STRING','INTEGER','DECIMAL','BOOLEAN','JSON')),
    category     TEXT        NOT NULL,
    description  TEXT        NOT NULL,
    critical     BOOLEAN     NOT NULL DEFAULT FALSE,  -- requires SETTINGS_MANAGE to change
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_by   BIGINT      REFERENCES users (id)
);

-- =====================================================================================
-- Seed data
-- =====================================================================================

INSERT INTO branches (code, name, address, phone)
VALUES ('MAIN', 'THEO TECH LTD - Main Shop', 'Kigali, Rwanda', NULL);

INSERT INTO permissions (name, module, description) VALUES
 ('DASHBOARD_VIEW',       'dashboard', 'View the dashboard'),
 ('USER_MANAGE',          'admin',     'Create, edit, disable and unlock users'),
 ('ROLE_MANAGE',          'admin',     'Create roles and assign permissions'),
 ('SETTINGS_MANAGE',      'admin',     'Change critical system settings (VAT, prefixes, stock policy)'),
 ('AUDIT_VIEW',           'admin',     'View the audit log'),
 ('BACKUP_MANAGE',        'admin',     'Run and download database backups'),
 ('PRODUCT_VIEW',         'catalog',   'View products, categories and brands'),
 ('PRODUCT_MANAGE',       'catalog',   'Create and edit products, categories and brands'),
 ('INVENTORY_VIEW',       'inventory', 'View stock levels and movements'),
 ('INVENTORY_ADJUST',     'inventory', 'Record stock adjustments and damages'),
 ('STOCK_ALLOW_NEGATIVE', 'inventory', 'Complete a sale that drives stock below zero (when the setting allows it)'),
 ('PURCHASE_VIEW',        'purchasing','View purchase orders and receipts'),
 ('PURCHASE_CREATE',      'purchasing','Record purchases and receive stock'),
 ('PURCHASE_RETURN',      'purchasing','Return goods to a supplier'),
 ('SUPPLIER_VIEW',        'purchasing','View suppliers'),
 ('SUPPLIER_MANAGE',      'purchasing','Create and edit suppliers'),
 ('SUPPLIER_PAYMENT',     'purchasing','Record payments to suppliers'),
 ('SALE_VIEW',            'sales',     'View sales history'),
 ('SALE_CREATE',          'sales',     'Use the point of sale'),
 ('SALE_DISCOUNT',        'sales',     'Apply discounts at the point of sale'),
 ('SALE_CUSTOM_PRICE',    'sales',     'Override the unit price at the point of sale'),
 ('SALE_RETURN',          'sales',     'Process customer returns'),
 ('SALE_VOID',            'sales',     'Void a completed sale'),
 ('CUSTOMER_VIEW',        'crm',       'View customers'),
 ('CUSTOMER_MANAGE',      'crm',       'Create and edit customers'),
 ('CUSTOMER_PAYMENT',     'crm',       'Record customer payments against credit'),
 ('EXPENSE_VIEW',         'finance',   'View expenses'),
 ('EXPENSE_MANAGE',       'finance',   'Record and edit expenses'),
 ('FINANCE_VIEW',         'finance',   'See cost prices, margins and profit'),
 ('REPORT_VIEW',          'reports',   'View reports'),
 ('REPORT_EXPORT',        'reports',   'Export reports to PDF/Excel'),
 ('EMPLOYEE_VIEW',        'hr',        'View employees'),
 ('EMPLOYEE_MANAGE',      'hr',        'Create and edit employees'),
 ('REPAIR_MANAGE',        'repairs',   'Log and update repair jobs'),
 ('NOTIFICATION_VIEW',    'system',    'See notifications');

INSERT INTO roles (name, description, system_role) VALUES
 ('ADMIN',        'Full access to everything, including critical settings', TRUE),
 ('MANAGER',      'Runs the shop: all operations and reports, no system administration', TRUE),
 ('SALES_STAFF',  'Point of sale and customers; never sees cost or profit', TRUE),
 ('STOCK_KEEPER', 'Products, purchases, suppliers and stock adjustments', TRUE),
 ('ACCOUNTANT',   'Finance: payments, expenses, reports, profit and audit', TRUE),
 ('TECHNICIAN',   'Repair jobs and read-only product/stock lookup', TRUE);

-- ADMIN: everything
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.name = 'ADMIN';

-- MANAGER: everything except system administration
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p
WHERE r.name = 'MANAGER' AND p.name NOT IN ('ROLE_MANAGE','SETTINGS_MANAGE','BACKUP_MANAGE');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('DASHBOARD_VIEW','PRODUCT_VIEW','INVENTORY_VIEW','SALE_VIEW','SALE_CREATE','SALE_DISCOUNT',
  'CUSTOMER_VIEW','CUSTOMER_MANAGE','CUSTOMER_PAYMENT','NOTIFICATION_VIEW')
WHERE r.name = 'SALES_STAFF';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('DASHBOARD_VIEW','PRODUCT_VIEW','PRODUCT_MANAGE','INVENTORY_VIEW','INVENTORY_ADJUST',
  'PURCHASE_VIEW','PURCHASE_CREATE','PURCHASE_RETURN','SUPPLIER_VIEW','SUPPLIER_MANAGE','NOTIFICATION_VIEW')
WHERE r.name = 'STOCK_KEEPER';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('DASHBOARD_VIEW','SALE_VIEW','PURCHASE_VIEW','CUSTOMER_VIEW','CUSTOMER_PAYMENT','SUPPLIER_VIEW',
  'SUPPLIER_PAYMENT','EXPENSE_VIEW','EXPENSE_MANAGE','EMPLOYEE_VIEW','REPORT_VIEW','REPORT_EXPORT',
  'FINANCE_VIEW','AUDIT_VIEW','NOTIFICATION_VIEW')
WHERE r.name = 'ACCOUNTANT';

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.name IN
 ('DASHBOARD_VIEW','PRODUCT_VIEW','INVENTORY_VIEW','CUSTOMER_VIEW','REPAIR_MANAGE','NOTIFICATION_VIEW')
WHERE r.name = 'TECHNICIAN';

-- Initial administrator. Password: Admin@123  (bcrypt) — must be changed at first login.
INSERT INTO users (username, email, full_name, password_hash, must_change_password, default_branch_id)
VALUES ('admin', 'admin@theotech.local', 'System Administrator',
        '{bcrypt}$2a$10$Cc1KlJpJgHjl8KOu2I8Ofu1YYvj2kN1CSo4UK8TBlngEkjouTiF8.', TRUE,
        (SELECT id FROM branches WHERE code = 'MAIN'));

INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id FROM users u, roles r WHERE u.username = 'admin' AND r.name = 'ADMIN';

INSERT INTO settings (key, value, value_type, category, description, critical) VALUES
 ('company.name',                 'THEO TECH LTD',        'STRING',  'company',  'Business name printed on receipts and reports', FALSE),
 ('company.address',              'Kigali, Rwanda',       'STRING',  'company',  'Business address', FALSE),
 ('company.phone',                '',                     'STRING',  'company',  'Business phone number', FALSE),
 ('company.tin',                  '',                     'STRING',  'company',  'Tax identification number', FALSE),
 ('company.currency',             'RWF',                  'STRING',  'company',  'Currency code', TRUE),
 ('company.timezone',             'Africa/Kigali',        'STRING',  'company',  'Business time zone', TRUE),
 ('sales.vat_rate',               '18',                   'DECIMAL', 'sales',    'VAT rate in percent applied to sales', TRUE),
 ('sales.vat_inclusive',          'true',                 'BOOLEAN', 'sales',    'Whether product prices already include VAT', TRUE),
 ('sales.allow_negative_stock',   'false',                'BOOLEAN', 'inventory','Allow authorised users to sell below zero stock', TRUE),
 ('sales.max_discount_percent',   '20',                   'DECIMAL', 'sales',    'Largest discount a cashier may apply without manager approval', TRUE),
 ('sales.low_stock_alerts',       'true',                 'BOOLEAN', 'inventory','Raise notifications when stock reaches the minimum level', FALSE),
 ('docs.invoice_prefix',          'INV',                  'STRING',  'documents','Prefix for sale invoice numbers', TRUE),
 ('docs.purchase_prefix',         'PUR',                  'STRING',  'documents','Prefix for purchase numbers', TRUE),
 ('docs.return_prefix',           'RET',                  'STRING',  'documents','Prefix for return numbers', TRUE),
 ('docs.receipt_footer',          'Thank you for shopping with THEO TECH LTD', 'STRING', 'documents', 'Text printed at the bottom of receipts', FALSE),
 ('security.max_failed_logins',   '5',                    'INTEGER', 'security', 'Failed login attempts before the account is locked', TRUE),
 ('security.lockout_minutes',     '15',                   'INTEGER', 'security', 'How long an automatic lock lasts', TRUE),
 ('security.session_timeout_min', '480',                  'INTEGER', 'security', 'Idle session timeout in minutes', TRUE),
 ('security.remember_me_days',    '14',                   'INTEGER', 'security', 'Remember-me token validity in days', TRUE);
