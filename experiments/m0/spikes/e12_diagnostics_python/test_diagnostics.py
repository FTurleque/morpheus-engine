from __future__ import annotations

import json
import unittest

from diagnostics import Diagnostic, create_diagnostic


class DiagnosticsTest(unittest.TestCase):
    def test_known_error_gets_catalog_severity(self) -> None:
        diagnostic = create_diagnostic("NO_PROVIDER_FOUND", "No provider found")
        self.assertEqual("ERROR", diagnostic.severity)

    def test_known_warning_gets_catalog_severity(self) -> None:
        diagnostic = create_diagnostic(
            "OPTIONAL_CAPABILITY_UNAVAILABLE",
            "History is unavailable",
        )
        self.assertEqual("WARNING", diagnostic.severity)

    def test_unknown_code_is_rejected(self) -> None:
        with self.assertRaises(ValueError):
            create_diagnostic("FREE_FORM_ERROR", "not allowed")

    def test_severity_cannot_contradict_catalog(self) -> None:
        with self.assertRaises(ValueError):
            Diagnostic(
                code="IDENTITY_COLLISION",
                severity="ERROR",
                message="collision",
            )

    def test_source_and_details_are_machine_readable(self) -> None:
        diagnostic = create_diagnostic(
            "UNRESOLVED_REFERENCE",
            "Target is absent",
            source="specs/auth/spec.md:20",
            details={"target": "minos:symbol:missing", "relation": "LINKS_TO_CODE"},
        )
        payload = diagnostic.to_dict()
        self.assertEqual("specs/auth/spec.md:20", payload["source"])
        self.assertEqual("minos:symbol:missing", payload["details"]["target"])

    def test_json_shape_is_stable_and_deterministic(self) -> None:
        diagnostic = create_diagnostic(
            "PARTIAL_INGESTION",
            "Some elements were preserved",
            details={"valid": 4, "invalid": 1},
        )
        first = diagnostic.to_json()
        second = diagnostic.to_json()
        self.assertEqual(first, second)
        decoded = json.loads(first)
        self.assertEqual(
            {"code", "message", "source", "details", "severity"},
            set(decoded),
        )

    def test_required_m0_diagnostic_codes_are_representable(self) -> None:
        codes = {
            "NO_PROVIDER_FOUND",
            "UNSUPPORTED_SOURCE",
            "UNSUPPORTED_FORMAT_VERSION",
            "MISSING_REQUIRED_CAPABILITY",
            "OPTIONAL_CAPABILITY_UNAVAILABLE",
            "IDENTITY_COLLISION",
            "UNRESOLVED_REFERENCE",
            "INVALID_SOURCE",
            "PARTIAL_INGESTION",
        }
        for code in codes:
            with self.subTest(code=code):
                diagnostic = create_diagnostic(code, code)
                self.assertEqual(code, diagnostic.code)


if __name__ == "__main__":
    unittest.main()
