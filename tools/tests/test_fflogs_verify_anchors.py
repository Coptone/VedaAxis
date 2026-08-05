import importlib.util
import json
import tempfile
import unittest
from pathlib import Path


MODULE_PATH = Path(__file__).parents[1] / "fflogs_verify_anchors.py"
SPEC = importlib.util.spec_from_file_location("fflogs_verify_anchors", MODULE_PATH)
fflogs_verify_anchors = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(fflogs_verify_anchors)


class FFLogsAnchorVerifierTests(unittest.TestCase):
    def test_compares_absolute_and_phase_local_timing(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report_dir = root / "REPORT" / "fight-2"
            (report_dir / "raw").mkdir(parents=True)
            (report_dir / "raw" / "metadata.json").write_text(
                json.dumps(
                    {
                        "data": {
                            "reportData": {
                                "report": {
                                    "fights": [
                                        {
                                            "id": 2,
                                            "name": "Test Fight",
                                            "startTime": 1000,
                                            "phaseTransitions": [{"id": 1, "startTime": 1000}],
                                        }
                                    ]
                                }
                            }
                        }
                    }
                ),
                encoding="utf-8",
            )
            (report_dir / "events.normalized.jsonl").write_text(
                json.dumps(
                    {
                        "timestamp": 1150,
                        "fightTimeMs": 150,
                        "type": "begincast",
                        "abilityGameId": 42,
                        "abilityName": "Test Anchor",
                    }
                )
                + "\n",
                encoding="utf-8",
            )
            seed = root / "seed.json"
            seed.write_text(
                json.dumps(
                    {
                        "phaseStartsSeconds": {"P1": 0},
                        "anchors": [
                            {
                                "anchorId": "anchor",
                                "phase": "P1",
                                "kind": "CAST_START",
                                "actionId": 42,
                                "occurrence": 1,
                                "plannedAtMs": 100,
                            }
                        ],
                    }
                ),
                encoding="utf-8",
            )

            result = fflogs_verify_anchors.verify_anchors(report_dir, seed, 100)

            self.assertEqual("MATCH", result["summary"]["status"])
            self.assertEqual(50, result["anchors"][0]["absoluteDriftMs"])
            self.assertEqual(50, result["anchors"][0]["phaseLocalDriftMs"])


if __name__ == "__main__":
    unittest.main()
