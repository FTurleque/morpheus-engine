from __future__ import annotations

import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPERIMENT_ROOT = HERE.parent.parent
OPEN_SPEC = EXPERIMENT_ROOT / "fixtures" / "openspec-basic"
UNSUPPORTED = EXPERIMENT_ROOT / "fixtures" / "openspec-unsupported-schema"
PARTIAL = EXPERIMENT_ROOT / "fixtures" / "openspec-partial"
SYNTHETIC = EXPERIMENT_ROOT / "fixtures" / "synthetic-basic"

sys.path.insert(0, str(HERE))

from provider_runtime import (  # noqa: E402
    fixed_adapter,
    normalize_with_provider,
    openspec_adapter,
    select_provider,
    synthetic_adapter,
)
from spike import normalize, probe  # noqa: E402


class ProviderDetectionTest(unittest.TestCase):
    def test_detects_supported_openspec_fixture(self) -> None:
        result = probe(OPEN_SPEC)

        self.assertTrue(result["supported"])
        self.assertEqual("openspec", result["provider"])
        self.assertEqual("spec-driven", result["schema"])
        self.assertIsNone(result["format_version"])
        self.assertEqual([], result["diagnostics"])
        self.assertIn("READ_ARCHIVES", result["capabilities"])
        self.assertNotIn("WRITE_CHANGE", result["capabilities"])
        self.assertNotIn("READ_ACCEPTANCE_CRITERIA", result["capabilities"])

    def test_rejects_unhandled_schema_explicitly(self) -> None:
        result = probe(UNSUPPORTED)
        self.assertFalse(result["supported"])
        self.assertEqual("research-first", result["schema"])
        self.assertEqual(["UNSUPPORTED_PROVIDER_SCHEMA"], result["diagnostics"])

    def test_missing_project_returns_explicit_diagnostic(self) -> None:
        result = probe(OPEN_SPEC / "does-not-exist")
        self.assertFalse(result["supported"])
        self.assertEqual(["NO_PROVIDER_FOUND"], result["diagnostics"])

    def test_selects_deterministically_when_multiple_providers_match(self) -> None:
        capabilities = {"DISCOVER_PROJECT", "READ_REQUIREMENTS"}
        result = select_provider(
            OPEN_SPEC,
            [
                fixed_adapter("zeta", capabilities=capabilities),
                fixed_adapter("alpha", capabilities=capabilities),
            ],
            required_capabilities={"READ_REQUIREMENTS"},
        )
        self.assertEqual("alpha", result["selected"])
        self.assertIn("MULTIPLE_PROVIDER_MATCHES", result["diagnostics"])

    def test_explicit_incompatible_provider_fails(self) -> None:
        result = select_provider(
            OPEN_SPEC,
            [fixed_adapter("alpha", capabilities={"DISCOVER_PROJECT"}, supported=False)],
            required_capabilities={"DISCOVER_PROJECT"},
            explicit_provider="alpha",
        )
        self.assertIsNone(result["selected"])
        self.assertEqual(["EXPLICIT_PROVIDER_INCOMPATIBLE"], result["diagnostics"])

    def test_missing_required_capability_fails(self) -> None:
        result = select_provider(
            OPEN_SPEC,
            [fixed_adapter("reader", capabilities={"DISCOVER_PROJECT"})],
            required_capabilities={"READ_REQUIREMENTS"},
        )
        self.assertIsNone(result["selected"])
        self.assertEqual(["MISSING_REQUIRED_CAPABILITY"], result["diagnostics"])

    def test_missing_optional_capability_degrades_explicitly(self) -> None:
        result = select_provider(
            OPEN_SPEC,
            [fixed_adapter("reader", capabilities={"DISCOVER_PROJECT", "READ_REQUIREMENTS"})],
            required_capabilities={"READ_REQUIREMENTS"},
            preferred_capabilities={"READ_HISTORY"},
        )
        self.assertEqual("reader", result["selected"])
        self.assertEqual(["READ_HISTORY"], result["missing_preferred_capabilities"])
        self.assertIn("OPTIONAL_CAPABILITY_UNAVAILABLE", result["diagnostics"])

    def test_remote_provider_requires_opt_in(self) -> None:
        remote = fixed_adapter(
            "remote-reader",
            capabilities={"DISCOVER_PROJECT", "READ_REQUIREMENTS"},
            remote=True,
        )
        denied = select_provider(
            OPEN_SPEC,
            [remote],
            required_capabilities={"READ_REQUIREMENTS"},
        )
        self.assertIsNone(denied["selected"])
        self.assertEqual(["REMOTE_PROVIDER_REQUIRES_OPT_IN"], denied["diagnostics"])

        allowed = select_provider(
            OPEN_SPEC,
            [remote],
            required_capabilities={"READ_REQUIREMENTS"},
            allow_remote=True,
        )
        self.assertEqual("remote-reader", allowed["selected"])
        self.assertTrue(allowed["remote"])

    def test_local_provider_wins_at_equal_capability(self) -> None:
        caps = {"DISCOVER_PROJECT", "READ_REQUIREMENTS"}
        result = select_provider(
            OPEN_SPEC,
            [
                fixed_adapter("remote", capabilities=caps, remote=True),
                fixed_adapter("local", capabilities=caps, remote=False),
            ],
            required_capabilities={"READ_REQUIREMENTS"},
            allow_remote=True,
        )
        self.assertEqual("local", result["selected"])


class DomainMappingTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.result = normalize(OPEN_SPEC)

    def test_current_baseline_and_proposed_delta_remain_distinct(self) -> None:
        current = {item["key"]: item for item in self.result["current"]["requirements"]}
        proposed = {
            item["key"]: item
            for item in self.result["proposed"]["changes"][0]["requirements"]
        }

        self.assertEqual(2, len(current))
        self.assertEqual(3, len(proposed))
        key = "auth-session/session-expiration"
        self.assertEqual("CURRENT", current[key]["temporal_state"])
        self.assertEqual("PROPOSED", proposed[key]["temporal_state"])
        self.assertEqual("MODIFIED", proposed[key]["delta_kind"])
        self.assertNotEqual(current[key]["statement"], proposed[key]["statement"])

    def test_change_artifacts_are_normalized_individually(self) -> None:
        change = self.result["proposed"]["changes"][0]
        self.assertEqual("add-remember-me", change["key"])
        self.assertTrue(change["proposal"]["intent"])
        self.assertEqual(4, len(change["proposal"]["scope"]))
        self.assertEqual(2, len(change["constraints"]))
        self.assertEqual(2, len(change["design_decisions"]))
        self.assertEqual(8, len(change["tasks"]))
        self.assertTrue(all(not task["completed"] for task in change["tasks"]))

    def test_historical_change_is_separate(self) -> None:
        historical = self.result["historical"]["changes"]
        self.assertEqual(1, len(historical))
        self.assertEqual("legacy-session-warning", historical[0]["key"])
        self.assertEqual("HISTORICAL", historical[0]["temporal_state"])
        self.assertEqual(0, len(self.result["diagnostics"]))

    def test_every_normalized_requirement_has_provenance(self) -> None:
        requirements = list(self.result["current"]["requirements"])
        requirements.extend(self.result["proposed"]["changes"][0]["requirements"])
        for item in requirements:
            with self.subTest(key=item["key"]):
                self.assertTrue(item["provenance"]["path"].endswith("spec.md"))
                self.assertGreater(item["provenance"]["line"], 0)
                self.assertGreaterEqual(len(item["scenarios"]), 1)

    def test_partial_source_is_reported_without_losing_valid_elements(self) -> None:
        result = normalize(PARTIAL)
        self.assertEqual(2, len(result["current"]["requirements"]))
        self.assertIn("PARTIAL_INGESTION", result["diagnostics"])

    def test_synthetic_provider_uses_same_normalized_contract_shape(self) -> None:
        open_result = normalize_with_provider(OPEN_SPEC, openspec_adapter())
        synthetic_result = normalize_with_provider(SYNTHETIC, synthetic_adapter())

        for result in (open_result, synthetic_result):
            self.assertEqual(
                {"probe", "current", "proposed", "historical", "diagnostics"},
                set(result.keys()),
            )
            self.assertIn("requirements", result["current"])
            self.assertIn("changes", result["proposed"])
            self.assertIn("changes", result["historical"])

        self.assertEqual("openspec", open_result["probe"]["provider"])
        self.assertEqual("synthetic-json", synthetic_result["probe"]["provider"])
        self.assertEqual("CURRENT", synthetic_result["current"]["requirements"][0]["temporal_state"])
        self.assertEqual("PROPOSED", synthetic_result["proposed"]["changes"][0]["temporal_state"])


if __name__ == "__main__":
    unittest.main()
