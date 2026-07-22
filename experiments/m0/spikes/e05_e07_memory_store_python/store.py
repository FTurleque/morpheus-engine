from __future__ import annotations

import copy
import hashlib
import json
from dataclasses import dataclass
from typing import Any


@dataclass
class KnowledgeSnapshot:
    snapshot_id: str
    source_revision: str | None
    predecessor: str | None
    fingerprint: str
    status: str
    payload: dict[str, Any] | None = None
    failure_reason: str | None = None


class SnapshotConflict(RuntimeError):
    pass


class InvalidSnapshot(RuntimeError):
    pass


class InMemorySpecificationKnowledgeStore:
    """Disposable E05/E07 store implementing observable atomic snapshot activation."""

    def __init__(self) -> None:
        self._next_id = 1
        self._snapshots: dict[str, KnowledgeSnapshot] = {}
        self._active_id: str | None = None
        self._fingerprints: dict[str, str] = {}

    @staticmethod
    def fingerprint(payload: dict[str, Any]) -> str:
        canonical = json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False)
        return hashlib.sha256(canonical.encode("utf-8")).hexdigest()

    def begin_snapshot(
        self,
        *,
        source_revision: str | None,
        payload: dict[str, Any],
        predecessor: str | None = None,
    ) -> KnowledgeSnapshot:
        fp = self.fingerprint(payload)
        existing = self._fingerprints.get(fp)
        if existing is not None:
            return self._snapshots[existing]

        if predecessor is None:
            predecessor = self._active_id

        snapshot_id = f"snap-{self._next_id:06d}"
        self._next_id += 1
        snapshot = KnowledgeSnapshot(
            snapshot_id=snapshot_id,
            source_revision=source_revision,
            predecessor=predecessor,
            fingerprint=fp,
            status="BUILDING",
            payload=copy.deepcopy(payload),
        )
        self._snapshots[snapshot_id] = snapshot
        self._fingerprints[fp] = snapshot_id
        return snapshot

    def validate(self, snapshot_id: str) -> KnowledgeSnapshot:
        snapshot = self._snapshots[snapshot_id]
        snapshot.status = "VALIDATING"
        payload = snapshot.payload
        if payload is None or "current" not in payload or "proposed" not in payload:
            snapshot.status = "FAILED"
            snapshot.failure_reason = "INVALID_SNAPSHOT_STRUCTURE"
            return snapshot

        current = payload.get("current", {})
        if "requirements" not in current:
            snapshot.status = "FAILED"
            snapshot.failure_reason = "MISSING_CURRENT_REQUIREMENTS"
            return snapshot

        snapshot.status = "READY"
        return snapshot

    def activate(self, snapshot_id: str) -> KnowledgeSnapshot:
        snapshot = self._snapshots[snapshot_id]
        if snapshot.status != "READY":
            raise InvalidSnapshot(f"snapshot {snapshot_id} is not READY")

        if snapshot.predecessor != self._active_id:
            raise SnapshotConflict(
                f"snapshot predecessor {snapshot.predecessor!r} != active {self._active_id!r}"
            )

        previous_id = self._active_id
        self._active_id = snapshot_id
        snapshot.status = "ACTIVE"
        if previous_id is not None:
            previous = self._snapshots[previous_id]
            if previous.status == "ACTIVE":
                previous.status = "RETIRED"
        return snapshot

    def fail(self, snapshot_id: str, reason: str) -> None:
        snapshot = self._snapshots[snapshot_id]
        snapshot.status = "FAILED"
        snapshot.failure_reason = reason

    def active_snapshot(self) -> KnowledgeSnapshot | None:
        if self._active_id is None:
            return None
        return self._snapshots[self._active_id]

    def get_snapshot(self, snapshot_id: str) -> KnowledgeSnapshot:
        return self._snapshots[snapshot_id]

    def get_current_specification(self) -> dict[str, Any]:
        active = self.active_snapshot()
        if active is None or active.payload is None:
            return {"specifications": 0, "requirements": []}
        return copy.deepcopy(active.payload["current"])

    def find_requirements(self, text: str = "") -> list[dict[str, Any]]:
        current = self.get_current_specification()
        needle = text.lower().strip()
        requirements = current.get("requirements", [])
        if not needle:
            return requirements
        return [
            item
            for item in requirements
            if needle in item.get("key", "").lower()
            or needle in item.get("title", "").lower()
            or needle in item.get("statement", "").lower()
        ]

    def get_change(self, change_key: str) -> dict[str, Any] | None:
        active = self.active_snapshot()
        if active is None or active.payload is None:
            return None
        for change in active.payload.get("proposed", {}).get("changes", []):
            if change.get("key") == change_key:
                return copy.deepcopy(change)
        return None

    def compare(self, left_id: str, right_id: str) -> dict[str, list[str]]:
        left = self._snapshots[left_id].payload or {}
        right = self._snapshots[right_id].payload or {}

        def reqs(payload: dict[str, Any]) -> dict[str, dict[str, Any]]:
            return {
                item["key"]: item
                for item in payload.get("current", {}).get("requirements", [])
            }

        a = reqs(left)
        b = reqs(right)
        added = sorted(set(b) - set(a))
        removed = sorted(set(a) - set(b))
        modified = sorted(key for key in set(a).intersection(b) if a[key] != b[key])
        unchanged = sorted(set(a).intersection(b) - set(modified))
        return {
            "ADDED": added,
            "REMOVED": removed,
            "MODIFIED": modified,
            "UNCHANGED": unchanged,
        }
