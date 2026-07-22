from __future__ import annotations

import json
import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPERIMENT_ROOT = HERE.parent.parent
E01_SPIKE = EXPERIMENT_ROOT / "spikes" / "e01_e02_python"
FIXTURE = EXPERIMENT_ROOT / "fixtures" / "openspec-basic"

sys.path.insert(0, str(E01_SPIKE))

from spike import normalize, probe  # noqa: E402
from context_builder import ChangeNotFound, build_change_context, to_compact_json  # noqa: E402


class CompactContextTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.payload = normalize(FIXTURE)
        cls.capabilities = probe(FIXTURE)["capabilities"]
        cls.context = build_change_context(
            cls.payload,
            "add-remember-me",
            provider_capabilities=cls.capabilities,
        )

    def test_contains_expected_top_level_fields(self) -> None:
        self.assertEqual(
            {
                "change",
                "temporal_state",
                "objective",
                "requirements",
                "constraints",
                "decisions",
                "acceptance_criteria",
                "tasks",
                "traceability",
                "provenance",
                "capability_gaps",
            },
            set(self.context),
        )

    def test_preserves_change_intent_and_temporal_state(self) -> None:
        self.assertEqual("add-remember-me", self.context["change"])
        self.assertEqual("PROPOSED", self.context["temporal_state"])
        self.assertIn("conserver volontairement", self.context["objective"])

    def test_compacts_supported_change_artifacts(self) -> None:
        self.assertEqual(3, len(self.context["requirements"]))
        self.assertEqual(2, len(self.context["constraints"]))
        self.assertEqual(2, len(self.context["decisions"]))
        self.assertEqual(8, len(self.context["tasks"]))

    def test_missing_acceptance_capability_is_explicit_not_invented(self) -> None:
        self.assertEqual([], self.context["acceptance_criteria"])
        self.assertEqual(["READ_ACCEPTANCE_CRITERIA"], self.context["capability_gaps"])

    def test_traceability_contains_change_to_requirement_edges(self) -> None:
        targets = {item["target"] for item in self.context["traceability"]}
        self.assertEqual(
            {
                "auth-session/session-expiration",
                "auth-session/explicit-remember-me-opt-in",
                "auth-session/persistent-credential-revocation",
            },
            targets,
        )
        self.assertTrue(all(item["relation"] == "AFFECTS" for item in self.context["traceability"]))

    def test_compact_json_is_deterministic_and_small(self) -> None:
        first = to_compact_json(self.context)
        second = to_compact_json(self.context)
        self.assertEqual(first, second)
        self.assertLess(len(first.encode("utf-8")), 4096)
        decoded = json.loads(first)
        self.assertEqual("add-remember-me", decoded["change"])

    def test_unknown_change_fails_explicitly(self) -> None:
        with self.assertRaises(ChangeNotFound):
            build_change_context(self.payload, "missing-change")


if __name__ == "__main__":
    unittest.main()
