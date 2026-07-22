from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPERIMENT_ROOT = HERE.parent.parent
MEMORY_SPIKE = EXPERIMENT_ROOT / "spikes" / "e05_e07_memory_store_python"
SQLITE_SPIKE = EXPERIMENT_ROOT / "spikes" / "e08_sqlite_store_python"

sys.path.insert(0, str(MEMORY_SPIKE))
from store import InMemorySpecificationKnowledgeStore  # noqa: E402

sys.path.insert(0, str(SQLITE_SPIKE))
from sqlite_store import SQLiteSpecificationKnowledgeStore  # noqa: E402


def trace_payload() -> dict:
    return {
        "current": {
            "specifications": 1,
            "requirements": [
                {
                    "key": "req:auth",
                    "title": "Auth",
                    "statement": "Authenticate",
                    "temporal_state": "CURRENT",
                }
            ],
        },
        "proposed": {"changes": []},
        "historical": {"changes": []},
        "traceability": [
            {
                "source": "scenario:login",
                "relation": "REFINES",
                "target": "req:auth",
                "origin": "EXPLICIT",
                "resolution": "RESOLVED",
                "evidence": "spec.md:10",
            },
            {
                "source": "change:remember",
                "relation": "AFFECTS",
                "target": "req:auth",
                "origin": "EXPLICIT",
                "resolution": "RESOLVED",
                "evidence": "delta.md:2",
            },
            {
                "source": "constraint:optin",
                "relation": "CONSTRAINS",
                "target": "change:remember",
                "origin": "EXPLICIT",
                "resolution": "RESOLVED",
                "evidence": "proposal.md:18",
            },
            {
                "source": "decision:token",
                "relation": "DEPENDS_ON",
                "target": "constraint:optin",
                "origin": "DERIVED",
                "resolution": "RESOLVED",
                "evidence": "resolver:v1",
            },
            {
                "source": "req:auth",
                "relation": "LINKS_TO_CODE",
                "target": "minos:symbol:missing",
                "origin": "EXPLICIT",
                "resolution": "UNRESOLVED",
                "evidence": "mapping.yaml:1",
            },
        ],
        "diagnostics": [],
    }


def publish_memory(store: InMemorySpecificationKnowledgeStore) -> None:
    snapshot = store.begin_snapshot(source_revision="r1", payload=trace_payload())
    store.validate(snapshot.snapshot_id)
    store.activate(snapshot.snapshot_id)


def publish_sqlite(store: SQLiteSpecificationKnowledgeStore) -> None:
    snapshot_id = store.begin_snapshot(source_revision="r1", payload=trace_payload())
    store.validate(snapshot_id)
    store.activate(snapshot_id)


class StoreTraceabilityContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.memory = InMemorySpecificationKnowledgeStore()
        self.sqlite = SQLiteSpecificationKnowledgeStore(Path(self.tmp.name) / "trace.sqlite")
        publish_memory(self.memory)
        publish_sqlite(self.sqlite)

    def tearDown(self) -> None:
        self.sqlite.close()
        self.tmp.cleanup()

    def stores(self):
        return (("memory", self.memory), ("sqlite", self.sqlite))

    def test_direct_trace_contract_matches(self) -> None:
        for name, store in self.stores():
            with self.subTest(store=name):
                paths = store.trace("change:remember", max_depth=1, bidirectional=False)
                self.assertEqual(1, len(paths))
                self.assertEqual("AFFECTS", paths[0]["relation"])
                self.assertEqual("req:auth", paths[0]["path"][-1])

    def test_inverse_trace_contract_matches(self) -> None:
        for name, store in self.stores():
            with self.subTest(store=name):
                paths = store.trace("req:auth", max_depth=1, bidirectional=True)
                targets = {path["path"][-1] for path in paths}
                self.assertIn("scenario:login", targets)
                self.assertIn("change:remember", targets)

    def test_depth_three_contract_matches(self) -> None:
        for name, store in self.stores():
            with self.subTest(store=name):
                paths = store.trace("decision:token", max_depth=3, bidirectional=False)
                self.assertTrue(any(path["depth"] == 3 for path in paths))
                self.assertTrue(any("req:auth" in path["path"] for path in paths))

    def test_unresolved_external_target_remains_visible(self) -> None:
        for name, store in self.stores():
            with self.subTest(store=name):
                paths = store.trace("req:auth", max_depth=1, bidirectional=False)
                unresolved = [
                    path
                    for path in paths
                    if path["path"][-1] == "minos:symbol:missing"
                ]
                self.assertEqual(1, len(unresolved))
                self.assertEqual("UNRESOLVED", unresolved[0]["resolution"])


if __name__ == "__main__":
    unittest.main()
