from __future__ import annotations

import sys
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPERIMENT_ROOT = HERE.parent.parent
E01_SPIKE = EXPERIMENT_ROOT / "spikes" / "e01_e02_python"
FIXTURE = EXPERIMENT_ROOT / "fixtures" / "openspec-basic"

sys.path.insert(0, str(E01_SPIKE))

from spike import normalize  # noqa: E402
from search import LexicalSearchEngine  # noqa: E402


class LexicalSearchTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.engine = LexicalSearchEngine(normalize(FIXTURE))

    def test_search_by_exact_key(self) -> None:
        result = self.engine.search("auth-session/session-expiration")
        self.assertGreaterEqual(len(result), 1)
        self.assertEqual("auth-session/session-expiration", result[0].key)
        self.assertEqual(0, result[0].score)

    def test_search_by_title(self) -> None:
        result = self.engine.search("persistent credential revocation")
        self.assertEqual("auth-session/persistent-credential-revocation", result[0].key)

    def test_search_by_statement_text(self) -> None:
        result = self.engine.search("30 minutes")
        self.assertTrue(any(item.temporal_state == "CURRENT" for item in result))
        self.assertTrue(any(item.temporal_state == "PROPOSED" for item in result))

    def test_filter_by_temporal_state(self) -> None:
        result = self.engine.search("session", temporal_states={"CURRENT"})
        self.assertTrue(result)
        self.assertTrue(all(item.temporal_state == "CURRENT" for item in result))

    def test_filter_by_kind(self) -> None:
        result = self.engine.search("remember", kinds={"ChangeProposal"})
        self.assertEqual(1, len(result))
        self.assertEqual("add-remember-me", result[0].key)

    def test_limit_and_order_are_deterministic(self) -> None:
        first = self.engine.search("session", limit=2)
        second = self.engine.search("session", limit=2)
        self.assertEqual(first, second)
        self.assertEqual(2, len(first))

    def test_empty_query_returns_nothing(self) -> None:
        self.assertEqual([], self.engine.search("   "))


if __name__ == "__main__":
    unittest.main()
