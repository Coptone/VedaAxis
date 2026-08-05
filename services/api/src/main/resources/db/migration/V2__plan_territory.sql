ALTER TABLE mitigation_plan
    ADD COLUMN territory_id BIGINT NOT NULL DEFAULT 1363;

CREATE INDEX idx_mitigation_plan_territory_match
    ON mitigation_plan(owner_id, territory_id, strategy_tag, track_mode);
