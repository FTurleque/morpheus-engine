from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from sqlite_store import (
    InvalidSnapshot,
    SnapshotConflict,
    SQLiteSpecificationKnowledgeStore,
)


def payload(statement: str, *, extra: bool = False) -> dict:
    requirements = [
        {
            "key": "demo/r1",
            "title": "R1",
            "statement": statement,
            "temporal_state": "CURRENT",
        }
    ]
    if extra:
        requirements.append(
            {
                "key": "demo/r2",
                "title": "R2",
                "statement": "extra",
                "temporal_state": "CURRENT",
            }
        )
    return {
        "current": {"specifications": 1, "requirements": requirements},
        "proposed": {
            "changes": [
                {
                    "key": "change-1",
                    "temporal_state": "PROPOSED",
                }
            ]
        },
        "historical": {"changes": []},
        "diagnostics": [],
    }


class SQLiteStoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.db = Path(self.tmp.name) / "morpheus-m0.sqlite"
        self.store = SQLiteSpecificationKnowledgeStore(self.db)

    def tearDown(self) -> None:
        self.store.close()
        self.tmp.cleanup()

    def publish(self, value: str, revision: str) -> str:
        sid = self.store.begin_snapshot(source_revision=revision, payload=payload(value))
        self.assertEqual("READY", self.store.validate(sid))
        self.store.activate(sid)
        return sid

    def test_persists_active_snapshot_across_reopen(self) -> None:
        sid = self.publish("v1", "r1")
        self.store.close()
        self.store = SQLiteSpecificationKnowledgeStore(self.db)
        self.assertEqual(sid, self.store.active_snapshot_id())
        self.assertEqual("v1", self.store.find_requirements()[0]["statement"])

    def test_building_snapshot_survives_reopen_without_becoming_active(self) -> None:
        v1 = self.publish("v1", "r1")
        v2 = self.store.begin_snapshot(source_revision="r2", payload=payload("v2"))
        self.store.close()
        self.store = SQLiteSpecificationKnowledgeStore(self.db)
        self.assertEqual(v1, self.store.active_snapshot_id())
        self.assertEqual("BUILDING", self.store.snapshot_status(v2))

    def test_failed_snapshot_does_not_replace_active(self) -> None:
        v1 = self.publish("v1", "r1")
        bad = self.store.begin_snapshot(
            source_revision="bad",
            payload={"proposed": {"changes": []}},
        )
        self.assertEqual("FAILED", self.store.validate(bad))
        with self.assertRaises(InvalidSnapshot):
            self.store.activate(bad)
        self.assertEqual(v1, self.store.active_snapshot_id())

    def test_atomic_activation_retires_previous(self) -> None:
        v1 = self.publish("v1", "r1")
        v2 = self.store.begin_snapshot(source_revision="r2", payload=payload("v2"))
        self.store.validate(v2)
        self.store.activate(v2)
        self.assertEqual(v2, self.store.active_snapshot_id())
        self.assertEqual("RETIRED", self.store.snapshot_status(v1))
        self.assertEqual("v2", self.store.find_requirements()[0]["statement"])

    def test_stale_predecessor_is_rejected_transactionally(self) -> None:
        v1 = self.publish("v1", "r1")
        a = self.store.begin_snapshot(source_revision="a", payload=payload("a"), predecessor=v1)
        b = self.store.begin_snapshot(source_revision="b", payload=payload("b"), predecessor=v1)
        self.store.validate(a)
        self.store.validate(b)
        self.store.activate(a)
        with self.assertRaises(SnapshotConflict):
            self.store.activate(b)
        self.assertEqual(a, self.store.active_snapshot_id())
        self.assertEqual("READY", self.store.snapshot_status(b))

    def test_identical_payload_is_idempotent(self) -> None:
        first = self.store.begin_snapshot(source_revision="r1", payload=payload("same"))
        second = self.store.begin_snapshot(source_revision="r1", payload=payload("same"))
        self.assertEqual(first, second)

    def test_queries_match_memory_store_semantics(self) -> None:
        sid = self.store.begin_snapshot(source_revision="r1", payload=payload("expiration policy"))
        self.store.validate(sid)
        self.store.activate(sid)
        self.assertEqual(1, len(self.store.find_requirements("expiration")))
        self.assertEqual("change-1", self.store.get_change("change-1")["key"])

    def test_compare_snapshots(self) -> None:
        v1 = self.store.begin_snapshot(source_revision="r1", payload=payload("v1"))
        self.store.validate(v1)
        self.store.activate(v1)
        v2 = self.store.begin_snapshot(source_revision="r2", payload=payload("v2", extra=True))
        self.store.validate(v2)
        diff = self.store.compare(v1, v2)
        self.assertEqual(["demo/r2"], diff["ADDED"])
        self.assertEqual(["demo/r1"], diff["MODIFIED"])


if __name__ == "__main__":
    unittest.main()
