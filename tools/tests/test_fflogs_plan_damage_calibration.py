import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "fflogs_plan_damage_calibration.py"
SPEC = importlib.util.spec_from_file_location("fflogs_plan_damage_calibration", MODULE_PATH)
module = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = module
SPEC.loader.exec_module(module)


class FFLogsPlanDamageCalibrationTests(unittest.TestCase):
    def test_discovers_only_complete_extractor_outputs(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            complete = root / "report-a" / "fight-1"
            incomplete = root / "report-b" / "fight-2"
            (complete / "raw").mkdir(parents=True)
            incomplete.mkdir(parents=True)
            (complete / "raw" / "metadata.json").write_text("{}", encoding="utf-8")
            (complete / "events.normalized.jsonl").write_text("", encoding="utf-8")
            (incomplete / "events.normalized.jsonl").write_text("", encoding="utf-8")

            self.assertEqual([complete.resolve()], module.discover_report_dirs(root))

    def metadata(self, start_time: int):
        return {"data": {"reportData": {"report": {
            "startTime": start_time,
            "fights": [{"id": 1, "startTime": 100, "friendlyPlayers": [1, 2, 3, 4]}],
            "masterData": {"actors": [
                {"id": 1, "type": "Player"}, {"id": 2, "type": "Player"},
                {"id": 3, "type": "Player"}, {"id": 4, "type": "Player"},
                {"id": 9, "type": "NPC"},
            ]},
        }}}}

    def events(self, offset: int):
        raidwide = [
            {"type": "calculateddamage", "fightTimeMs": 1_000 + offset, "sourceId": 9,
             "targetId": target, "abilityGameId": 100, "abilityName": "Raidwide",
             "unmitigatedAmount": amount}
            for target, amount in zip((1, 2, 3, 4), (90_000, 100_000, 110_000, 120_000))
        ]
        tankbuster = [
            {"type": "calculateddamage", "fightTimeMs": time + offset, "sourceId": 9,
             "targetId": 1, "abilityGameId": 200, "abilityName": "Triple Buster",
             "unmitigatedAmount": amount}
            for time, amount in ((2_000, 100_000), (3_500, 120_000), (5_000, 140_000))
        ]
        return raidwide + tankbuster

    def test_matches_and_sums_multi_hit_damage_without_emitting_identifiers(self):
        reports = [
            module.build_report_sample(self.metadata(index * 10_000), self.events(index * 100))
            for index in range(3)
        ]
        plan = {"encounterId": "test", "mechanics": [
            {"mechanicId": "aoe", "phase": "P1", "name": "AOE", "plannedAtMs": 1_000,
             "type": "RAIDWIDE", "damageType": "MAGICAL"},
            {"mechanicId": "buster", "phase": "P1", "name": "TB x3", "plannedAtMs": 2_000,
             "type": "TANK_BUSTER", "damageType": "PHYSICAL"},
        ]}

        result = module.build_calibration(plan, reports, collected_at="2026-08-06")

        self.assertFalse(result["containsPlayerNames"])
        self.assertFalse(result["containsReportCodes"])
        self.assertEqual(2, len(result["mechanics"]))
        by_id = {item["mechanicId"]: item for item in result["mechanics"]}
        self.assertEqual(120_000, by_id["aoe"]["amount"])
        self.assertEqual(360_000, by_id["buster"]["amount"])
        self.assertEqual(3, by_id["buster"]["reportSampleCount"])


if __name__ == "__main__":
    unittest.main()
