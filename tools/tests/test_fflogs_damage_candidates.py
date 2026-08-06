import importlib.util
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "fflogs_damage_candidates.py"
SPEC = importlib.util.spec_from_file_location("fflogs_damage_candidates", MODULE_PATH)
fflogs_damage_candidates = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(fflogs_damage_candidates)


class FFLogsDamageCandidateTests(unittest.TestCase):
    def test_builds_anonymized_enemy_damage_candidates(self):
        metadata = {
            "data": {"reportData": {"report": {
                "fights": [{"id": 2, "encounterID": 1085, "friendlyPlayers": [1, 2, 9]}],
                "masterData": {"actors": [
                    {"id": 1, "name": "Alice", "type": "Player"},
                    {"id": 2, "name": "Bob", "type": "Player"},
                    {"id": 9, "name": "Boss", "type": "NPC"},
                ]},
            }}}
        }
        events = [
            {"type": "calculateddamage", "packetId": 50, "timestamp": 1000, "fightTimeMs": 100,
             "sourceId": 9, "targetId": 1, "abilityGameId": 123, "abilityName": "Ruin",
             "amount": 80, "unmitigatedAmount": 100, "multiplier": 0.8},
            {"type": "calculateddamage", "packetId": 50, "timestamp": 1000, "fightTimeMs": 100,
             "sourceId": 9, "targetId": 2, "abilityGameId": 123, "abilityName": "Ruin",
             "amount": 90, "unmitigatedAmount": 110, "multiplier": 0.82},
            {"type": "calculateddamage", "timestamp": 1100, "sourceId": 1, "targetId": 9,
             "abilityGameId": 999, "abilityName": "Player skill", "amount": 1000},
        ]

        result = fflogs_damage_candidates.build_candidates(metadata, events)

        self.assertFalse(result["containsPlayerNames"])
        self.assertFalse(result["promotionAllowed"])
        self.assertEqual(1, result["actionCount"])
        action = result["actions"][0]
        self.assertEqual(123, action["actionId"])
        self.assertEqual("MULTI_TARGET_CANDIDATE", action["targetPattern"])
        self.assertEqual(2, action["maximumTargetsPerObservedCast"])
        self.assertEqual(85, action["actualAmount"]["median"])
        self.assertNotIn("Alice", str(result))


if __name__ == "__main__":
    unittest.main()
