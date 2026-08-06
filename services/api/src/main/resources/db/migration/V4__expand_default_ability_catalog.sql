INSERT INTO ability_definition(action_id, name, job_ids, cooldown_ms, max_charges, duration_ms, confirmation_strategy, source, confidence) VALUES
    (7531, '铁壁 / Rampart', '19,21,32,37', 90000, 1, 20000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (36923, '戕戮 / Damnation', '21', 120000, 1, 15000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'POC_PENDING'),
    (40, '战栗 / Thrill of Battle', '21', 90000, 1, 10000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (25751, '原初的血气 / Bloodwhetting', '21', 25000, 1, 8000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (43, '死斗 / Holmgang', '21', 240000, 1, 10000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (36935, '大星云 / Great Nebula', '37', 120000, 1, 15000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'POC_PENDING'),
    (25758, '刚玉之心 / Heart of Corundum', '37', 25000, 1, 8000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (16152, '超火流星 / Superbolide', '37', 360000, 1, 10000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (7432, '神祝祷 / Divine Benison', '24', 30000, 2, 15000, 'STATUS_APPLY', 'Official Job Guide 2026-08-06', 'REVIEWED'),
    (25861, '水流幕 / Aquaveil', '24', 60000, 1, 8000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (3569, '庇护所 / Asylum', '24', 90000, 1, 24000, 'ACTION_EFFECT', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (7433, '全大赦 / Plenary Indulgence', '24', 60000, 1, 10000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (25862, '礼仪之铃 / Liturgy of the Bell', '24', 180000, 1, 20000, 'ACTION_EFFECT', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (37011, '神爱抚 / Divine Caress', '24', 1000, 1, 30000, 'ACTION_EFFECT', 'XIVAPI Action sheet 2026-08-06', 'POC_PENDING'),
    (24300, '活化 / Zoe', '40', 90000, 1, 30000, 'STATUS_APPLY', 'Official Job Guide 2026-08-06', 'REVIEWED'),
    (37034, '均衡预后 II / Eukrasian Prognosis II', '40', 1500, 1, 30000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'POC_PENDING'),
    (24302, '自生 II / Physis II', '40', 60000, 1, 15000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (24318, '魂灵风息 / Pneuma', '40', 120000, 1, 0, 'ACTION_EFFECT', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (24305, '输血 / Haima', '40', 120000, 1, 15000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (24303, '坚角清汁 / Taurochole', '40', 45000, 1, 15000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'POC_PENDING'),
    (24317, '拯救 / Krasis', '40', 60000, 1, 10000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'POC_PENDING'),
    (37035, '智慧之爱 / Philosophia', '40', 180000, 1, 20000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'POC_PENDING'),
    (24291, '均衡诊断 / Eukrasian Diagnosis', '40', 1500, 1, 30000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (7549, '牵制 / Feint', '20,22,30,34,39,41', 90000, 1, 15000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED'),
    (16012, '防守之桑巴 / Shield Samba', '38', 90000, 1, 15000, 'STATUS_APPLY', 'Official Job Guide 2026-08-06', 'REVIEWED'),
    (7560, '昏乱 / Addle', '25,27,35,42', 90000, 1, 15000, 'STATUS_APPLY', 'XIVAPI Action sheet 2026-08-06', 'REVIEWED');

UPDATE ability_definition SET name = '雪仇 / Reprisal', source = 'XIVAPI Action sheet 2026-08-06' WHERE action_id = 7535;
UPDATE ability_definition SET name = '摆脱 / Shake It Off', source = 'XIVAPI Action sheet 2026-08-06' WHERE action_id = 7388;
UPDATE ability_definition SET name = '光之心 / Heart of Light', source = 'XIVAPI Action sheet 2026-08-06' WHERE action_id = 16160;
UPDATE ability_definition SET name = '节制 / Temperance', source = 'XIVAPI Action sheet 2026-08-06' WHERE action_id = 16536;
UPDATE ability_definition SET name = '白牛清汁 / Kerachole', source = 'XIVAPI Action sheet 2026-08-06' WHERE action_id = 24298;
UPDATE ability_definition SET name = '整体论 / Holos', source = 'XIVAPI Action sheet 2026-08-06' WHERE action_id = 24310;
UPDATE ability_definition SET name = '泛输血 / Panhaima', source = 'XIVAPI Action sheet 2026-08-06' WHERE action_id = 24311;
