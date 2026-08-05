CREATE TABLE app_user (
    id VARCHAR(36) PRIMARY KEY,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE refresh_token (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    audience VARCHAR(32) NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE device_authorization (
    id VARCHAR(36) PRIMARY KEY,
    device_code_hash VARCHAR(64) NOT NULL UNIQUE,
    user_code VARCHAR(12) NOT NULL UNIQUE,
    device_name VARCHAR(120) NOT NULL,
    status VARCHAR(24) NOT NULL,
    user_id VARCHAR(36) REFERENCES app_user(id),
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    consumed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE authorized_device (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    name VARCHAR(120) NOT NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE ability_definition (
    action_id BIGINT PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    job_ids VARCHAR(200) NOT NULL,
    cooldown_ms BIGINT NOT NULL,
    max_charges INTEGER NOT NULL,
    duration_ms BIGINT NOT NULL,
    confirmation_strategy VARCHAR(32) NOT NULL,
    source VARCHAR(80) NOT NULL,
    confidence VARCHAR(24) NOT NULL
);

CREATE TABLE mitigation_plan (
    id VARCHAR(36) PRIMARY KEY,
    owner_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    name VARCHAR(160) NOT NULL,
    encounter_id VARCHAR(36) NOT NULL,
    strategy_tag VARCHAR(80) NOT NULL,
    track_mode VARCHAR(16) NOT NULL,
    draft_json TEXT NOT NULL,
    latest_version INTEGER NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_mitigation_plan_owner ON mitigation_plan(owner_id, updated_at);
CREATE INDEX idx_mitigation_plan_match ON mitigation_plan(encounter_id, strategy_tag, track_mode);

CREATE TABLE plan_version (
    id VARCHAR(36) PRIMARY KEY,
    plan_id VARCHAR(36) NOT NULL REFERENCES mitigation_plan(id),
    version_number INTEGER NOT NULL,
    status VARCHAR(24) NOT NULL,
    snapshot_json TEXT NOT NULL,
    share_code VARCHAR(24) NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(plan_id, version_number)
);

CREATE INDEX idx_plan_version_plan ON plan_version(plan_id, version_number);

CREATE TABLE fight_execution (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL REFERENCES app_user(id),
    plan_id VARCHAR(36) NOT NULL,
    plan_version INTEGER NOT NULL,
    result VARCHAR(24) NOT NULL,
    payload_json TEXT NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ended_at TIMESTAMP WITH TIME ZONE NOT NULL,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
    UNIQUE(user_id, id)
);

INSERT INTO ability_definition(action_id, name, job_ids, cooldown_ms, max_charges, duration_ms, confirmation_strategy, source, confidence) VALUES
    (7535, 'Reprisal', '19,21,32,37', 60000, 1, 15000, 'STATUS_APPLY', 'Lumina seed', 'REVIEWED'),
    (7388, 'Shake It Off', '21', 90000, 1, 15000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED'),
    (3540, 'Divine Veil', '19', 90000, 1, 30000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED'),
    (16471, 'Dark Missionary', '32', 90000, 1, 15000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED'),
    (16160, 'Heart of Light', '37', 90000, 1, 15000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED'),
    (188, 'Sacred Soil', '28', 30000, 1, 15000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED'),
    (16536, 'Temperance', '24', 120000, 1, 20000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED'),
    (24298, 'Kerachole', '40', 30000, 1, 15000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED'),
    (24310, 'Holos', '40', 120000, 1, 20000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED'),
    (24311, 'Panhaima', '40', 120000, 1, 15000, 'STATUS_APPLY', 'Lumina seed', 'UNVERIFIED');
