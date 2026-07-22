from __future__ import annotations

import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPERIMENT_ROOT = HERE.parent.parent
FIXTURE = EXPERIMENT_ROOT / "fixtures" / "openspec-basic"

sys.path.insert(0, str(HERE))

from spike import normalize, probe  # noqa: E402


class ProviderDetectionTest(unittest.TestCase):
    def test_detects_openspec_fixture(self) -> None:
        result = probe(FIXTURE)

        self.assertTrue(result["supported"])
        self.assertEqual("openspec", result["provider"])
        self.assertEqual("spec-driven", result["schema"])
        self.assertEqual([], result["diagnostics"])

        required = {
            "DISCOVER_PROJECT",
            "READ_CURRENT_SPECIFICATIONS",
            "READ_CHANGES",
            "READ_REQUIREMENTS",
            "READ_SCENARIOS",
            "READ_DESIGN_DECISIONS",
            "READ_IMPLEMENTATION_TASKS",
        }
        self.assertTrue(required.issubset(set(result["capabilities"])))

    def test_does_not_claim_acceptance_criteria_without_mapping_rule(self) -> None:
        result = probe(FIXTURE)
        self.assertNotIn("READ_ACCEPTANCE_CRITERIA", result["capabilities"])

    def test_missing_project_returns_explicit_diagnostic(self) -> None:
        result = probe(FIXTURE / "does-not-exist")
        self.assertFalse(result["supported"])
        self.assertEqual(["NO_PROVIDER_FOUND"], result["diagnostics"])


class DomainMappingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.result = normalize(FIXTURE)

    def test_current_baseline_contains_two_requirements(self) -> None:
        requirements = self.result["current"]["requirements"]
        keys = {item["key"] for item in requirements}

        self.assertEqual(1, self.result["current"]["specifications"])
        self.assertEqual(2, len(requirements))
        self.assertEqual(
            {
                "auth-session/session-expiration",
                "auth-session/session-activity-refresh",
            },
            keys,
        )
        self.assertTrue(all(item["temporal_state"] == "CURRENT" for item in requirements))

    def test_proposed_change_remains_separate_from_current(self) -> None:
        changes = self.result["proposed"]["changes"]
        self.assertEqual(1, len(changes))

        change = changes[0]
        self.assertEqual("add-remember-me", change["key"])
        self.assertEqual("PROPOSED", change["temporal_state"])
        self.assertEqual(8, change["task_count"])
        self.assertGreaterEqual(change["design_decision_count"], 2)

        requirements = change["requirements"]
        self.assertEqual(3, len(requirements))
        self.assertTrue(all(item["temporal_state"] == "PROPOSED" for item in requirements))

        delta_by_key = {item["key"]: item["delta_kind"] for item in requirements}
        self.assertEqual(
            "MODIFIED", delta_by_key["auth-session/session-expiration"]
        )
        self.assertEqual(
            "ADDED", delta_by_key["auth-session/explicit-remember-me-opt-in"]
        )
        self.assertEqual(
            "ADDED", delta_by_key["auth-session/persistent-credential-revocation"]
        )

    def test_modified_requirement_does_not_replace_current_baseline(self) -> None:
        current = {
            item["key"]: item for item in self.result["current"]["requirements"]
        }
        proposed = {
            item["key"]: item
            for item in self.result["proposed"]["changes"][0]["requirements"]
        }

        key = "auth-session/session-expiration"
        self.assertIn(key, current)
        self.assertIn(key, proposed)
        self.assertEqual("CURRENT", current[key]["temporal_state"])
        self.assertEqual("PROPOSED", proposed[key]["temporal_state"])
        self.assertNotEqual(current[key]["statement"], proposed[key]["statement"])

    def test_every_requirement_has_source_provenance(self) -> None:
        requirements = list(self.result["current"]["requirements"])
        requirements.extend(self.result["proposed"]["changes"][0]["requirements"])

        for item in requirements:
            with self.subTest(key=item["key"]):
                provenance = item["provenance"]
                self.assertTrue(provenance["path"].endswith("spec.md"))
                self.assertGreater(provenance["line"], 0)
                self.assertGreaterEqual(len(item["scenarios"]), 1)


if __name__ == "__main__":
    unittest.main()
