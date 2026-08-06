import importlib.util
import sys
import unittest
from pathlib import Path


TOOLS_DIR = Path(__file__).parents[1]
sys.path.insert(0, str(TOOLS_DIR))
MODULE_PATH = TOOLS_DIR / "fflogs_collect_samples.py"
SPEC = importlib.util.spec_from_file_location("fflogs_collect_samples", MODULE_PATH)
module = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = module
assert SPEC.loader is not None
SPEC.loader.exec_module(module)


class FFLogsSampleCollectorTests(unittest.TestCase):
    def test_selects_recent_unique_kills_for_the_requested_encounter(self):
        reports = [
            {
                "code": "A",
                "startTime": 1_000,
                "fights": [
                    {"id": 1, "encounterID": 1085, "kill": True, "startTime": 10},
                    {"id": 2, "encounterID": 1085, "kill": False, "startTime": 20},
                ],
            },
            {
                "code": "B",
                "startTime": 2_000,
                "fights": [{"id": 3, "encounterID": 1085, "kill": True, "startTime": 10}],
            },
            {
                "code": "DUPLICATE",
                "startTime": 1_500,
                "fights": [{"id": 4, "encounterID": 1085, "kill": True, "startTime": 510}],
            },
            {
                "code": "OTHER",
                "startTime": 3_000,
                "fights": [{"id": 5, "encounterID": 9999, "kill": True, "startTime": 10}],
            },
        ]

        selected = module.select_unique_kills(reports, encounter_id=1085, sample_limit=5)

        self.assertEqual(2, len(selected))
        self.assertEqual(2_010, selected[0].absolute_start_ms)
        self.assertEqual(1_010, selected[1].absolute_start_ms)
        self.assertNotEqual(selected[0].report_code, selected[1].report_code)


if __name__ == "__main__":
    unittest.main()
