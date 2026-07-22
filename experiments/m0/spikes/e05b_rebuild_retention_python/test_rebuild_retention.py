from __future__ import annotations

import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPERIMENT_ROOT = HERE.parent.parent
E01_SPIKE = EXPERIMENT_ROOT / "spikes" / "e01_e02_python"
MEMORY_SPIKE = EXPERIMENT_ROOT / "spikes" / "e05_e07_memory_store_python"
SQLITE_SPIKE = EXPERIMENT_ROOT / "spikes" / "e08_sqlite_store_python"
FIXTURE = EXPERIMENT_ROOT / "fixtures" / "openspec-basic"

sys.path.insert(0, str(E01_SPIKE))
from spike import normalize  # noqa: E402

sys.path.insert(0, str(MEMORY_SPIKE))
from store import InMemorySpecificationKnowledgeStore  # noqa: E402

sys.path.insert(0, str(SQLITE_SPIKE))
from sqlite_store import SQLiteSpecificationKnowledgeStore  # noqa: E402


def simple_payload(value: str) -> dict:
    return {
        "current": {
            "specifications": 1,
            "requirements": [
                {
                    "key": "demo/r1",
                    "title": "R1",
                    "statement": value,
                    "temporal_state": "CURRENT",
                }
            ],
        },
        "proposed": {"changes": []},
        "historical": {"changes": []},
        "traceability": [],
        "diagnostics": [],
    }


def publish_memory(store: InMemorySpecificationKnowledgeStore, payload: dict, revision: str) -> str:
    snapshot = store.begin_snapshot(source_revision=revision, payload=payload)
    store.validate(snapshot.snapshot_id)
    store.activate(snapshot.snapshot_id)
    return snapshot.snapshot_id


def publish_sqlite(store: SQLiteSpecificationKnowledgeStore, payload: dict, revision: str) -> str:
    snapshot_id = store.begin_snapshot(source_revision=revision, payload=payload)
    store.validate(snapshot_id)
    store.activate(snapshot_id)
    return snapshot_id


class RebuildRetentionTest(unittest.TestCase):
    def test_memory_retention_keeps_active_and_one_recent_retired(self) -> None:
        store = InMemorySpecificationKnowledgeStore()
        v1 = publish_memory(store, simple_payload("v1"), "r1")
        v2 = publish_memory(store, simple_payload("v2"), "r2")
        v3 = publish_memory(store, simple_payload("v3"), "r3")

        removed = store.prune_retired(keep_recent=1)
        self.assertEqual([v1], removed)
        self.assertEqual(v3, store.active_snapshot().snapshot_id)
        self.assertEqual("RETIRED", store.get_snapshot(v2).status)
        with self.assertRaises(KeyError):
            store.get_snapshot(v1)

    def test_sqlite_retention_keeps_active_and_one_recent_retired(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            store = SQLiteSpecificationKnowledgeStore(Path(directory) / "retention.sqlite")
            try:
                v1 = publish_sqlite(store, simple_payload("v1"), "r1")
                v2 = publish_sqlite(store, simple_payload("v2"), "r2")
                v3 = publish_sqlite(store, simple_payload("v3"), "r3")

                removed = store.prune_retired(keep_recent=1)
                self.assertEqual([v1], removed)
                self.assertEqual(v3, store.active_snapshot_id())
                self.assertEqual("RETIRED", store.snapshot_status(v2))
                with self.assertRaises(KeyError):
                    store.snapshot_status(v1)
            finally:
                store.close()

    def test_memory_store_can_be_rebuilt_from_sources(self) -> None:
        payload = normalize(FIXTURE)
        first = InMemorySpecificationKnowledgeStore()
        publish_memory(first, payload, "fixture-r1")
        expected = first.get_current_specification()

        rebuilt = InMemorySpecificationKnowledgeStore()
        publish_memory(rebuilt, normalize(FIXTURE), "fixture-r1")
        self.assertEqual(expected, rebuilt.get_current_specification())
        self.assertEqual(
            first.fingerprint(payload),
            rebuilt.fingerprint(normalize(FIXTURE)),
        )

    def test_sqlite_store_can_be_deleted_and_rebuilt_from_sources(self) -> None:
        payload = normalize(FIXTURE)
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "rebuild.sqlite"
            first = SQLiteSpecificationKnowledgeStore(path)
            publish_sqlite(first, payload, "fixture-r1")
            expected = first.get_current_specification()
            first.close()
            path.unlink()

            rebuilt = SQLiteSpecificationKnowledgeStore(path)
            try:
                publish_sqlite(rebuilt, normalize(FIXTURE), "fixture-r1")
                self.assertEqual(expected, rebuilt.get_current_specification())
            finally:
                rebuilt.close()

    def test_rebuild_preserves_current_proposed_boundary(self) -> None:
        payload = normalize(FIXTURE)
        store = InMemorySpecificationKnowledgeStore()
        publish_memory(store, payload, "fixture-r1")
        current_keys = {item["key"] for item in store.get_current_specification()["requirements"]}
        proposed = store.get_change("add-remember-me")
        proposed_keys = {item["key"] for item in proposed["requirements"]}
        self.assertIn("auth-session/session-expiration", current_keys)
        self.assertIn("auth-session/session-expiration", proposed_keys)
        self.assertEqual("PROPOSED", proposed["temporal_state"])


if __name__ == "__main__":
    unittest.main()
