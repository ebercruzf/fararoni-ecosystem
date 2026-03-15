-- FASE 43.3.5 - Schema de Canales Dinamicos
-- Fararoni Framework - Data-Driven Channel Activation

CREATE TABLE IF NOT EXISTS agency_channels (
    id VARCHAR(64) PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    name VARCHAR(128),
    config_json TEXT NOT NULL,
    status VARCHAR(16) DEFAULT 'ACTIVE',
    created_at INTEGER DEFAULT (strftime('%s', 'now')),
    updated_at INTEGER DEFAULT (strftime('%s', 'now')),
    created_by VARCHAR(64)
);

CREATE INDEX IF NOT EXISTS idx_channels_status ON agency_channels(status);

CREATE INDEX IF NOT EXISTS idx_channels_type ON agency_channels(type);

CREATE TABLE IF NOT EXISTS channel_audit_log (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    channel_id VARCHAR(64) NOT NULL,
    action VARCHAR(16) NOT NULL,
    old_config_hash VARCHAR(64),
    new_config_hash VARCHAR(64),
    changed_by VARCHAR(64),
    changed_at INTEGER DEFAULT (strftime('%s', 'now')),
    ip_address VARCHAR(45),
    user_agent TEXT
);

CREATE INDEX IF NOT EXISTS idx_audit_channel ON channel_audit_log(channel_id);

CREATE INDEX IF NOT EXISTS idx_audit_date ON channel_audit_log(changed_at);

CREATE TABLE IF NOT EXISTS channel_secrets (
    channel_id VARCHAR(64) PRIMARY KEY,
    encrypted_secret BLOB NOT NULL,
    encryption_iv BLOB NOT NULL,
    auth_tag BLOB NOT NULL,
    encryption_version INTEGER DEFAULT 1,
    key_id VARCHAR(64),
    rotated_at INTEGER,
    created_at INTEGER DEFAULT (strftime('%s', 'now')),
    FOREIGN KEY (channel_id) REFERENCES agency_channels(id) ON DELETE CASCADE
);
