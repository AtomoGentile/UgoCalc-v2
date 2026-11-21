-- UgoCalc Cloudflare D1 (SQLite serverless) schema
-- Piano Cloudflare Free: fino a 5 GB di storage, 5M letture/giorno, 100k scritture/giorno (0 EUR/mese).

CREATE TABLE IF NOT EXISTS accounts (
    login_id TEXT PRIMARY KEY,
    account_id TEXT NOT NULL,
    global_bias_enabled INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS events (
    account_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT (datetime('now')),
    session_mode TEXT NOT NULL DEFAULT '',
    ai_model_id TEXT NOT NULL DEFAULT '',
    strategy TEXT NOT NULL DEFAULT '',
    food_category TEXT NOT NULL DEFAULT '',
    segment_key TEXT NOT NULL DEFAULT '',
    payload_json TEXT NOT NULL,
    PRIMARY KEY (account_id, event_id)
);

CREATE INDEX IF NOT EXISTS idx_events_account_type
    ON events (account_id, event_type, created_at);

CREATE INDEX IF NOT EXISTS idx_events_type
    ON events (event_type);

CREATE TABLE IF NOT EXISTS config (
    pk TEXT NOT NULL,
    sk TEXT NOT NULL DEFAULT 'v1',
    min_benchmark_meals INTEGER NOT NULL DEFAULT 30,
    v2_shadow_enabled INTEGER NOT NULL DEFAULT 0,
    maintenance_message TEXT NOT NULL DEFAULT '',
    force_update_min_version TEXT NOT NULL DEFAULT '',
    PRIMARY KEY (pk, sk)
);

CREATE TABLE IF NOT EXISTS versions (
    pk TEXT PRIMARY KEY,
    latest_version TEXT NOT NULL,
    min_required_version TEXT NOT NULL,
    changelog TEXT NOT NULL DEFAULT '',
    apk_url TEXT NOT NULL DEFAULT '',
    mandatory INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS invites (
    token TEXT PRIMARY KEY,
    owner_login_id TEXT NOT NULL,
    guest_login_id TEXT,
    status TEXT NOT NULL DEFAULT 'pending',
    ttl INTEGER NOT NULL
);

-- Valori iniziali di default
INSERT OR IGNORE INTO config (pk, sk, min_benchmark_meals, v2_shadow_enabled, maintenance_message, force_update_min_version)
VALUES ('global', 'v1', 30, 0, '', '');

INSERT OR IGNORE INTO versions (pk, latest_version, min_required_version, changelog, apk_url, mandatory)
VALUES ('android', '3.0.4', '3.0.4', 'Local-First & Free Backend', '', 0);

CREATE TABLE IF NOT EXISTS users (
    id TEXT PRIMARY KEY,
    email TEXT NOT NULL UNIQUE,
    pw_hash TEXT NOT NULL,
    pw_salt TEXT NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until INTEGER NOT NULL DEFAULT 0,
    created_at TEXT NOT NULL DEFAULT (datetime('now'))
);

