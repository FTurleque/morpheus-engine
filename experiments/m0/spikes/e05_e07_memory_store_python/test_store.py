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
from store import (  # noqa: E402
    InMemorySpecificationKnowledgeStore,
    InvalidSnapshot,
    SnapshotConflict,
)


def payload_with_statement(statement: str) -> dict:
    return {
        "current": {
            "specifications": 1,
            "requirements": [
                {
                    "key": "demo/r1",
                    "title": "R1",
                    "statement": statement,
                    "temporal_state": "CURRENT",
                }
            ],
        },
        "proposed": {"changes": []},
        "historical": {"changes": []},
        "diagnostics": [],
    }


class SnapshotStoreTest(unittest.TestCase):
    def setUp(self) -> None:
        self.store = InMemorySpecificationKnowledgeStore()

    def publish(self, payload: dict, revision: str) -> str:
        snap = self.store.begin_snapshot(source_revision=revision, payload=payload)
        self.assertEqual("READY", self.store.validate(snap.snapshot_id).status)
        self.store.activate(snap.snapshot_id)
        return snap.snapshot_id

    def test_activate_v1(self) -> None:
        sid = self.publish(payload_with_statement("v1"), "rev-1")
        self.assertEqual(sid, self.store.active_snapshot().snapshot_id)
        self.assertEqual("v1", self.store.find_requirements()[0]["statement"])

    def test_building_v2_does_not_replace_v1(self) -> None:
        v1 = self.publish(payload_with_statement("v1"), "rev-1")
        v2 = self.store.begin_snapshot(source_revision="rev-2", payload=payload_with_statement("v2"))
        self.assertEqual("BUILDING", v2.status)
        self.assertEqual(v1, self.store.active_snapshot().snapshot_id)
        self.assertEqual("v1", self.store.find_requirements()[0]["statement"])

    def test_failed_v2_keeps_v1_active(self) -> None:
        v1 = self.publish(payload_with_statement("v1"), "rev-1")
        invalid = {"proposed": {"changes": []}}
        v2 = self.store.begin_snapshot(source_revision="rev-2", payload=invalid)
        self.assertEqual("FAILED", self.store.validate(v2.snapshot_id).status)
        self.assertEqual(v1, self.store.active_snapshot().snapshot_id)
        with self.assertRaises(InvalidSnapshot):
            self.store.activate(v2.snapshot_id)

    def test_ready_v2_activates_atomically_and_retires_v1(self) -> None:
        v1 = self.publish(payload_with_statement("v1"), "rev-1")
        v2 = self.store.begin_snapshot(source_revision="rev-2", payload=payload_with_statement("v2"))
        self.store.validate(v2.snapshot_id)
        self.store.activate(v2.snapshot_id)
        self.assertEqual(v2.snapshot_id, self.store.active_snapshot().snapshot_id)
        self.assertEqual("RETIRED", self.store.get_snapshot(v1).status)
        self.assertEqual("v2", self.store.find_requirements()[0]["statement"])

    def test_stale_predecessor_cannot_overwrite_newer_active_snapshot(self) -> None:
        v1 = self.publish(payload_with_statement("v1"), "rev-1")
        candidate_a = self.store.begin_snapshot(
            source_revision="rev-a",
            payload=payload_with_statement("a"),
            predecessor=v1,
        )
        candidate_b = self.store.begin_snapshot(
            source_revision="rev-b",
            payload=payload_with_statement("b"),
            predecessor=v1,
        )
        self.store.validate(candidate_a.snapshot_id)
        self.store.validate(candidate_b.snapshot_id)
        self.store.activate(candidate_a.snapshot_id)
        with self.assertRaises(SnapshotConflict):
            self.store.activate(candidate_b.snapshot_id)
        self.assertEqual(candidate_a.snapshot_id, self.store.active_snapshot().snapshot_id)

    def test_replay_identical_payload_is_idempotent(self) -> None:
        payload = payload_with_statement("same")
        first = self.store.begin_snapshot(source_revision="rev-1", payload=payload)
        second = self.store.begin_snapshot(source_revision="rev-1", payload=payload)
        self.assertEqual(first.snapshot_id, second.snapshot_id)

    def test_compare_snapshots(self) -> None:
        v1_payload = payload_with_statement("v1")
        v2_payload = payload_with_statement("v2")
        v2_payload["current"]["requirements"].append(
            {
                "key": "demo/r2",
                "title": "R2",
                "statement": "added",
                "temporal_state": "CURRENT",
            }
        )
        v1 = self.store.begin_snapshot(source_revision="r1", payload=v1_payload)
        self.store.validate(v1.snapshot_id)
        self.store.activate(v1.snapshot_id)
        v2 = self.store.begin_snapshot(source_revision="r2", payload=v2_payload)
        self.store.validate(v2.snapshot_id)
        diff = self.store.compare(v1.snapshot_id, v2.snapshot_id)
        self.assertEqual(["demo/r2"], diff["ADDED"])
        self.assertEqual(["demo/r1"], diff["MODIFIED"])

    def test_vertical_slice_queries_on_normalized_openspec_payload(self) -> None:
        payload = normalize(FIXTURE)
        sid = self.publish(payload, "fixture-rev-1")
        self.assertEqual(sid, self.store.active_snapshot().snapshot_id)
        self.assertEqual(2, len(self.store.find_requirements()))
        self.assertEqual(1, len(self.store.find_requirements("expiration")))
        change = self.store.get_change("add-remember-me")
        self.assertIsNotNone(change)
        self.assertEqual("PROPOSED", change["temporal_state"])


if __name__ == "__main__":
    unittest.main()
