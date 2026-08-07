ALTER TABLE ability_definition
    ADD COLUMN cast_category VARCHAR(20) NOT NULL DEFAULT 'OGCD';

UPDATE ability_definition
SET cast_category = 'GCD'
WHERE action_id IN (
    185,   -- Adloquium
    186,   -- Succor
    24291, -- Eukrasian Diagnosis
    24292, -- Eukrasian Prognosis
    24318, -- Pneuma
    37034  -- Eukrasian Prognosis II
);
