from __future__ import annotations

import hashlib
import json
import sqlite3
from collections import deque
from pathlib import Path
from typing import Any


class SnapshotConflict(RuntimeError):
    pass


class InvalidSnapshot(RuntimeError):
    pass


class SQLiteSpecificationKnowledgeStore:
    """Disposable SQLite E08 candidate. Payload-as-JSON is experimental, not final schema."""

    def __init__(self, path: Path) -> None:
        self.path = Path(path)
        self.connection = sqlite3.connect(self.path)
        self.connection.row_factory = sqlite3.Row
        self._init_schema()

    def close(self) -> None:
        self.connection.close()

    def _init_schema(self) -> None:
        with self.connection:
            self.connection.executescript(
                """
                CREATE TABLE IF NOT EXISTS snapshots (
                    snapshot_id TEXT PRIMARY KEY,
                    source_revision TEXT,
                    predecessor TEXT,
                    fingerprint TEXT NOT NULL UNIQUE,
                    status TEXT NOT NULL,
                    payload_json TEXT,
                    failure_reason TEXT
                );
                CREATE TABLE IF NOT EXISTS trace_links (
                    snapshot_id TEXT NOT NULL,
                    source TEXT NOT NULL,
                    relation TEXT NOT NULL,
                    target TEXT NOT NULL,
                    origin TEXT,
                    resolution TEXT,
                    evidence TEXT,
                    PRIMARY KEY(snapshot_id, source, relation, target, origin, resolution, evidence),
                    FOREIGN KEY(snapshot_id) REFERENCES snapshots(snapshot_id) ON DELETE CASCADE
                );
                CREATE INDEX IF NOT EXISTS idx_trace_source
                    ON trace_links(snapshot_id, source);
                CREATE INDEX IF NOT EXISTS idx_trace_target
                    ON trace_links(snapshot_id, target);
                CREATE TABLE IF NOT EXISTS meta (
                    key TEXT PRIMARY KEY,
                    value TEXT
                );
                INSERT OR IGNORE INTO meta(key, value) VALUES ('next_id', '1');
                INSERT OR IGNORE INTO meta(key, value) VALUES ('active_snapshot', NULL);
                """
            )

    @staticmethod
    def _canonical(payload: dict[str, Any]) -> str:
        return json.dumps(payload, sort_keys=True, separators=(",", ":"), ensure_ascii=False)

    @classmethod
    def fingerprint(cls, payload: dict[str, Any]) -> str:
        return hashlib.sha256(cls._canonical(payload).encode("utf-8")).hexdigest()

    def _active_id(self) -> str | None:
        row = self.connection.execute(
            "SELECT value FROM meta WHERE key='active_snapshot'"
        ).fetchone()
        return row["value"] if row else None

    def _next_id(self) -> str:
        with self.connection:
            row = self.connection.execute(
                "SELECT value FROM meta WHERE key='next_id'"
            ).fetchone()
            value = int(row["value"])
            self.connection.execute(
                "UPDATE meta SET value=? WHERE key='next_id'", (str(value + 1),)
            )
        return f"snap-{value:06d}"

    def begin_snapshot(
        self,
        *,
        source_revision: str | None,
        payload: dict[str, Any],
        predecessor: str | None = None,
    ) -> str:
        fp = self.fingerprint(payload)
        existing = self.connection.execute(
            "SELECT snapshot_id FROM snapshots WHERE fingerprint=?", (fp,)
        ).fetchone()
        if existing:
            return existing["snapshot_id"]

        if predecessor is None:
            predecessor = self._active_id()
        snapshot_id = self._next_id()
        with self.connection:
            self.connection.execute(
                """
                INSERT INTO snapshots(
                    snapshot_id, source_revision, predecessor, fingerprint, status, payload_json
                ) VALUES (?, ?, ?, ?, 'BUILDING', ?)
                """,
                (
                    snapshot_id,
                    source_revision,
                    predecessor,
                    fp,
                    self._canonical(payload),
                ),
            )
            for link in payload.get("traceability", []):
                self.connection.execute(
                    """
                    INSERT OR IGNORE INTO trace_links(
                        snapshot_id, source, relation, target, origin, resolution, evidence
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        snapshot_id,
                        link.get("source"),
                        link.get("relation"),
                        link.get("target"),
                        link.get("origin"),
                        link.get("resolution"),
                        json.dumps(link.get("evidence"), sort_keys=True, ensure_ascii=False)
                        if isinstance(link.get("evidence"), (dict, list))
                        else link.get("evidence"),
                    ),
                )
        return snapshot_id

    def validate(self, snapshot_id: str) -> str:
        row = self.connection.execute(
            "SELECT payload_json FROM snapshots WHERE snapshot_id=?", (snapshot_id,)
        ).fetchone()
        if row is None:
            raise KeyError(snapshot_id)
        payload = json.loads(row["payload_json"]) if row["payload_json"] else None
        with self.connection:
            self.connection.execute(
                "UPDATE snapshots SET status='VALIDATING', failure_reason=NULL WHERE snapshot_id=?",
                (snapshot_id,),
            )
            if payload is None or "current" not in payload or "proposed" not in payload:
                self.connection.execute(
                    "UPDATE snapshots SET status='FAILED', failure_reason='INVALID_SNAPSHOT_STRUCTURE' WHERE snapshot_id=?",
                    (snapshot_id,),
                )
                return "FAILED"
            if "requirements" not in payload.get("current", {}):
                self.connection.execute(
                    "UPDATE snapshots SET status='FAILED', failure_reason='MISSING_CURRENT_REQUIREMENTS' WHERE snapshot_id=?",
                    (snapshot_id,),
                )
                return "FAILED"
            self.connection.execute(
                "UPDATE snapshots SET status='READY' WHERE snapshot_id=?", (snapshot_id,)
            )
        return "READY"

    def activate(self, snapshot_id: str) -> None:
        cursor = self.connection.cursor()
        try:
            cursor.execute("BEGIN IMMEDIATE")
            row = cursor.execute(
                "SELECT status, predecessor FROM snapshots WHERE snapshot_id=?", (snapshot_id,)
            ).fetchone()
            if row is None:
                raise KeyError(snapshot_id)
            if row["status"] != "READY":
                raise InvalidSnapshot(snapshot_id)

            active = cursor.execute(
                "SELECT value FROM meta WHERE key='active_snapshot'"
            ).fetchone()["value"]
            if row["predecessor"] != active:
                raise SnapshotConflict(
                    f"snapshot predecessor {row['predecessor']!r} != active {active!r}"
                )

            if active is not None:
                cursor.execute(
                    "UPDATE snapshots SET status='RETIRED' WHERE snapshot_id=? AND status='ACTIVE'",
                    (active,),
                )
            cursor.execute(
                "UPDATE snapshots SET status='ACTIVE' WHERE snapshot_id=?", (snapshot_id,)
            )
            cursor.execute(
                "UPDATE meta SET value=? WHERE key='active_snapshot'", (snapshot_id,)
            )
            self.connection.commit()
        except Exception:
            self.connection.rollback()
            raise

    def snapshot_status(self, snapshot_id: str) -> str:
        row = self.connection.execute(
            "SELECT status FROM snapshots WHERE snapshot_id=?", (snapshot_id,)
        ).fetchone()
        if row is None:
            raise KeyError(snapshot_id)
        return row["status"]

    def active_snapshot_id(self) -> str | None:
        return self._active_id()

    def _payload(self, snapshot_id: str) -> dict[str, Any]:
        row = self.connection.execute(
            "SELECT payload_json FROM snapshots WHERE snapshot_id=?", (snapshot_id,)
        ).fetchone()
        if row is None:
            raise KeyError(snapshot_id)
        return json.loads(row["payload_json"])

    def get_current_specification(self) -> dict[str, Any]:
        active = self._active_id()
        if active is None:
            return {"specifications": 0, "requirements": []}
        return self._payload(active)["current"]

    def find_requirements(self, text: str = "") -> list[dict[str, Any]]:
        requirements = self.get_current_specification().get("requirements", [])
        needle = text.lower().strip()
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
        active = self._active_id()
        if active is None:
            return None
        payload = self._payload(active)
        for change in payload.get("proposed", {}).get("changes", []):
            if change.get("key") == change_key:
                return change
        return None

    def trace(
        self,
        start: str,
        *,
        max_depth: int = 3,
        bidirectional: bool = True,
    ) -> list[dict[str, Any]]:
        active = self._active_id()
        if active is None or max_depth <= 0:
            return []

        queue = deque([(start, 0, [start])])
        visited = {start}
        paths: list[dict[str, Any]] = []

        while queue:
            node, depth, path = queue.popleft()
            if depth >= max_depth:
                continue
            rows = list(
                self.connection.execute(
                    """
                    SELECT source, relation, target, origin, resolution, evidence, 0 AS inverse
                    FROM trace_links WHERE snapshot_id=? AND source=?
                    """,
                    (active, node),
                )
            )
            if bidirectional:
                rows.extend(
                    self.connection.execute(
                        """
                        SELECT target AS source, relation, source AS target,
                               origin, resolution, evidence, 1 AS inverse
                        FROM trace_links WHERE snapshot_id=? AND target=?
                        """,
                        (active, node),
                    )
                )

            for row in rows:
                target = row["target"]
                new_path = [*path, target]
                paths.append(
                    {
                        "depth": depth + 1,
                        "path": new_path,
                        "relation": row["relation"],
                        "origin": row["origin"],
                        "resolution": row["resolution"],
                        "evidence": row["evidence"],
                        "inverse": bool(row["inverse"]),
                    }
                )
                if target not in visited:
                    visited.add(target)
                    queue.append((target, depth + 1, new_path))
        return paths

    def prune_retired(self, *, keep_recent: int = 1) -> list[str]:
        if keep_recent < 0:
            raise ValueError("keep_recent must be >= 0")
        rows = list(
            self.connection.execute(
                "SELECT snapshot_id FROM snapshots WHERE status='RETIRED' ORDER BY snapshot_id DESC"
            )
        )
        to_remove = [row["snapshot_id"] for row in rows[keep_recent:]]
        if not to_remove:
            return []
        with self.connection:
            for snapshot_id in to_remove:
                self.connection.execute(
                    "DELETE FROM trace_links WHERE snapshot_id=?", (snapshot_id,)
                )
                self.connection.execute(
                    "DELETE FROM snapshots WHERE snapshot_id=?", (snapshot_id,)
                )
        return sorted(to_remove)

    def compare(self, left_id: str, right_id: str) -> dict[str, list[str]]:
        def reqs(snapshot_id: str) -> dict[str, dict[str, Any]]:
            payload = self._payload(snapshot_id)
            return {
                item["key"]: item
                for item in payload.get("current", {}).get("requirements", [])
            }

        a = reqs(left_id)
        b = reqs(right_id)
        modified = sorted(key for key in set(a).intersection(b) if a[key] != b[key])
        return {
            "ADDED": sorted(set(b) - set(a)),
            "REMOVED": sorted(set(a) - set(b)),
            "MODIFIED": modified,
            "UNCHANGED": sorted(set(a).intersection(b) - set(modified)),
        }
