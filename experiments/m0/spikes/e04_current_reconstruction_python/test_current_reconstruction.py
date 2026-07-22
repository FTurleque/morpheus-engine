from __future__ import annotations

import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPERIMENT_ROOT = HERE.parent.parent
E01_SPIKE = EXPERIMENT_ROOT / "spikes" / "e01_e02_python"
FIXTURE = EXPERIMENT_ROOT / "fixtures" / "openspec-state-matrix"

sys.path.insert(0, str(E01_SPIKE))

from spike import normalize  # noqa: E402


class CurrentReconstructionTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.result = normalize(FIXTURE)

    def test_current_baseline_ignores_active_deltas(self) -> None:
        current = self.result["current"]["requirements"]
        self.assertEqual(1, len(current))
        self.assertEqual("CURRENT", current[0]["temporal_state"])
        self.assertIn("30 minutes", current[0]["statement"])

    def test_concurrent_proposed_changes_remain_distinct(self) -> None:
        changes = {change["key"]: change for change in self.result["proposed"]["changes"]}
        self.assertEqual({"extend-timeout", "shorten-timeout"}, set(changes))
        extend = changes["extend-timeout"]["requirements"][0]
        shorten = changes["shorten-timeout"]["requirements"][0]
        self.assertEqual("PROPOSED", extend["temporal_state"])
        self.assertEqual("PROPOSED", shorten["temporal_state"])
        self.assertEqual(extend["key"], shorten["key"])
        self.assertNotEqual(extend["statement"], shorten["statement"])

    def test_historical_delta_is_queryable_but_not_current(self) -> None:
        historical = self.result["historical"]["changes"]
        self.assertEqual(1, len(historical))
        archived = historical[0]
        self.assertEqual("HISTORICAL", archived["temporal_state"])
        self.assertEqual("auth-session/legacy-mode", archived["requirements"][0]["key"])

        current_keys = {item["key"] for item in self.result["current"]["requirements"]}
        self.assertNotIn("auth-session/legacy-mode", current_keys)

    def test_archived_change_does_not_imply_baseline_promotion(self) -> None:
        current_statements = [item["statement"] for item in self.result["current"]["requirements"]]
        self.assertTrue(all("legacy mode" not in statement.lower() for statement in current_statements))


if __name__ == "__main__":
    unittest.main()
