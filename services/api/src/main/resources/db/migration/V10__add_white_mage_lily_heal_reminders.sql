UPDATE ability_definition
SET max_charges = 2,
    source = 'Official Job Guide + XIVAPI Action sheet 2026-08-10'
WHERE action_id = 3570;

DELETE FROM ability_definition
WHERE action_id IN (16531, 16534);

INSERT INTO ability_definition(
    action_id, name, job_ids, cooldown_ms, max_charges, duration_ms,
    confirmation_strategy, source, confidence, icon_path, cast_category
) VALUES
    (16531, '安慰之心 / Afflatus Solace', '24', 2500, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI Action sheet 2026-08-10', 'REVIEWED', 'ui/icon/002000/002640.tex', 'GCD'),
    (16534, '狂喜之心 / Afflatus Rapture', '24', 2500, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI Action sheet 2026-08-10', 'REVIEWED', 'ui/icon/002000/002643.tex', 'GCD');
