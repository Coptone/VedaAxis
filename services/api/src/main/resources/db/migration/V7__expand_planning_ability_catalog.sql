UPDATE ability_definition
SET name = '混合 / Krasis',
    cooldown_ms = 60000,
    duration_ms = 10000,
    source = 'Official Job Guide + XIVAPI 2026-08-07',
    confidence = 'REVIEWED',
    icon_path = 'ui/icon/003000/003685.tex'
WHERE action_id = 24317;

UPDATE ability_definition
SET cooldown_ms = 120000,
    source = 'Official Job Guide + XIVAPI 2026-08-07'
WHERE action_id = 24300;

UPDATE ability_definition
SET cooldown_ms = 120000,
    source = 'Official Job Guide + XIVAPI 2026-08-07'
WHERE action_id = 3585;

UPDATE ability_definition
SET cooldown_ms = 90000,
    source = 'Official Job Guide + XIVAPI 2026-08-07'
WHERE action_id = 16542;

UPDATE ability_definition
SET name = '炽天化 / Seraphism',
    source = 'Official Job Guide + XIVAPI 2026-08-07'
WHERE action_id = 37014;

DELETE FROM ability_definition WHERE action_id IN (
    140, 189, 24294, 24296, 24299, 24301, 24309,
    3570, 3571, 3583, 3586, 3587, 3612, 3614,
    7434, 7437, 7439, 7445, 8324,
    16537, 16538, 16543, 16545, 16553, 16557,
    37024, 37025, 37027, 37028
);

INSERT INTO ability_definition(
    action_id, name, job_ids, cooldown_ms, max_charges, duration_ms,
    confirmation_strategy, source, confidence, icon_path
) VALUES
    (140, '天赐祝福 / Benediction', '24', 180000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002627.tex'),
    (3570, '神名 / Tetragrammaton', '24', 60000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002633.tex'),
    (3571, '法令 / Assize', '24', 40000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002634.tex'),

    (189, '生命活性法 / Lustrate', '28', 1000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002805.tex'),
    (3583, '不屈不挠之策 / Indomitability', '28', 30000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002806.tex'),
    (3586, '应急战术 / Emergency Tactics', '28', 15000, 1, 15000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002809.tex'),
    (3587, '转化 / Dissipation', '28', 180000, 1, 30000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002810.tex'),
    (7434, '深谋远虑之策 / Excogitation', '28', 45000, 1, 45000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002813.tex'),
    (7437, '以太契约 / Aetherpact', '28', 3000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002816.tex'),
    (16537, '仙光的低语 / Whispering Dawn', '28', 60000, 1, 21000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002852.tex'),
    (16538, '异想的幻光 / Fey Illumination', '28', 120000, 1, 20000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002853.tex'),
    (16543, '仙光的祝福 / Fey Blessing', '28', 60000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002854.tex'),
    (16545, '炽天召唤 / Summon Seraph', '28', 120000, 1, 22000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/002000/002850.tex'),

    (3612, '星位合图 / Synastry', '33', 120000, 1, 20000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003139.tex'),
    (3614, '先天禀赋 / Essential Dignity', '33', 40000, 2, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003141.tex'),
    (7439, '地星 / Earthly Star', '33', 60000, 1, 20000, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003143.tex'),
    (8324, '星体爆轰 / Stellar Detonation', '33', 3000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003144.tex'),
    (7445, '王冠之贵妇 / Lady of Crowns', '33', 1000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003146.tex'),
    (16553, '天星冲日 / Celestial Opposition', '33', 60000, 1, 15000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003142.tex'),
    (16557, '天宫图 / Horoscope', '33', 60000, 1, 10000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003550.tex'),
    (37024, '放浪神之箭 / the Arrow', '33', 55000, 1, 15000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003112.tex'),
    (37025, '建筑神之塔 / the Spire', '33', 55000, 1, 30000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003115.tex'),
    (37027, '世界树之干 / the Bole', '33', 55000, 1, 15000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003111.tex'),
    (37028, '河流神之瓶 / the Ewer', '33', 55000, 1, 15000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003114.tex'),

    (24294, '拯救 / Soteria', '40', 90000, 1, 15000, 'STATUS_APPLY', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003662.tex'),
    (24296, '灵橡清汁 / Druochole', '40', 1000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003664.tex'),
    (24299, '寄生清汁 / Ixochole', '40', 30000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003667.tex'),
    (24301, '消化 / Pepsis', '40', 30000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003669.tex'),
    (24309, '根素 / Rhizomata', '40', 90000, 1, 0, 'ACTION_EFFECT', 'Official Job Guide + XIVAPI 2026-08-07', 'REVIEWED', 'ui/icon/003000/003677.tex');
