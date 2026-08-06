import importlib.util
import os
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch


MODULE_PATH = Path(__file__).parents[1] / "fflogs_extract.py"
SPEC = importlib.util.spec_from_file_location("fflogs_extract", MODULE_PATH)
fflogs_extract = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(fflogs_extract)


class FFLogsExtractorTests(unittest.TestCase):
    def test_loads_local_env_without_overriding_process_values(self):
        with tempfile.TemporaryDirectory() as directory:
            env_file = Path(directory) / ".env"
            env_file.write_text(
                "FFLOGS_CLIENT_ID=file-id\nFFLOGS_CLIENT_SECRET='file-secret'\n",
                encoding="utf-8",
            )
            with patch.dict(os.environ, {"FFLOGS_CLIENT_ID": "process-id"}, clear=True):
                fflogs_extract.load_env_file(env_file)
                self.assertEqual("process-id", os.environ["FFLOGS_CLIENT_ID"])
                self.assertEqual("file-secret", os.environ["FFLOGS_CLIENT_SECRET"])

    def test_parses_cn_report_url(self):
        self.assertEqual(
            "WdgtVGLAmj73Mbr8",
            fflogs_extract.parse_report_code(
                "https://cn.fflogs.com/reports/WdgtVGLAmj73Mbr8?fight=2&type=damage-done"
            ),
        )

    def test_normalizes_action_event(self):
        normalized = fflogs_extract.normalize_event(
            "ABC123",
            2,
            {
                "timestamp": 1234,
                "type": "cast",
                "sourceID": 7,
                "targetID": 9,
                "packetID": 12,
                "multiplier": 0.8,
                "ability": {"gameID": 7535, "name": "Reprisal"},
            },
            fight_start_time=1000,
        )
        self.assertEqual(7535, normalized["abilityGameId"])
        self.assertEqual("Reprisal", normalized["abilityName"])
        self.assertEqual(234, normalized["fightTimeMs"])
        self.assertEqual("cast", normalized["type"])
        self.assertEqual(12, normalized["packetId"])
        self.assertEqual(0.8, normalized["multiplier"])
        self.assertNotIn("amount", normalized)


if __name__ == "__main__":
    unittest.main()
