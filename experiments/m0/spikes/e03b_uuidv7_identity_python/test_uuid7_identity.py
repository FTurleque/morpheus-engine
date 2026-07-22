from __future__ import annotations

import sys
import tempfile
import unittest
import uuid
from pathlib import Path

HERE = Path(__file__).resolve().parent
EXPERIMENT_ROOT = HERE.parent.parent
MEMORY_SPIKE = EXPERIMENT_ROOT / "spikes" / "e05_e07_memory_store_python"
SQLITE_SPIKE = EXPERIMENT_ROOT / "spikes" / "e08_sqlite_store_python"

sys.path.insert(0, str(MEMORY_SPIKE))
from store import InMemorySpecificationKnowledgeStore  # noqa: E402

sys.path.insert(0, str(SQLITE_SPIKE))
from sqlite_store import SQLiteSpecificationKnowledgeStore  # noqa: E402

from uuid7_identity import canonical, extract_unix_ts_ms, generate_uuid7  # noqa: E402

RFC_SAMPLE = "017f22e2-79b0-7cc3-98c4-dc0c0c07398f"
RFC_SAMPLE_TS_MS = 1645557742000


def payload(domain_id: str, statement: str) -> dict:
    return {
        "current": {
            "specifications": 1,
            "requirements": [
                {
                    "domain_id": domain_id,
                    "key": "demo/r1",
                    "title": "Requirement",
                    "statement": statement,
                    "temporal_state": "CURRENT",
                }
            ],
        },
        "proposed": {"changes": []},
        "historical": {"changes": []},
        "traceability": [],
        "diagnostics": [],
    }


class UUIDv7IdentityTest(unittest.TestCase):
    def test_rfc_sample_is_version_7_and_round_trips(self) -> None:
        value = uuid.UUID(RFC_SAMPLE)
        self.assertEqual(7, value.version)
        self.assertEqual(RFC_SAMPLE, canonical(value))
        self.assertEqual(RFC_SAMPLE_TS_MS, extract_unix_ts_ms(value))

    def test_generator_is_local_and_produces_version_7(self) -> None:
        value = generate_uuid7(now_ms=RFC_SAMPLE_TS_MS, random_bits=123456789)
        self.assertEqual(7, value.version)
        self.assertEqual(RFC_SAMPLE_TS_MS, extract_unix_ts_ms(value))

    def test_ids_from_later_milliseconds_sort_after_earlier_ids(self) -> None:
        earlier = generate_uuid7(now_ms=1000, random_bits=0)
        later = generate_uuid7(now_ms=1001, random_bits=0)
        self.assertLess(earlier.int, later.int)

    def test_ten_thousand_ids_in_same_millisecond_are_unique(self) -> None:
        values = {generate_uuid7(now_ms=1234567890) for _ in range(10_000)}
        self.assertEqual(10_000, len(values))

    def test_memory_store_preserves_uuidv7_as_opaque_domain_id(self) -> None:
        domain_id = canonical(generate_uuid7(now_ms=RFC_SAMPLE_TS_MS, random_bits=1))
        store = InMemorySpecificationKnowledgeStore()
        first = store.begin_snapshot(source_revision="r1", payload=payload(domain_id, "v1"))
        store.validate(first.snapshot_id)
        store.activate(first.snapshot_id)
        self.assertEqual(domain_id, store.find_requirements()[0]["domain_id"])

        second = store.begin_snapshot(source_revision="r2", payload=payload(domain_id, "v2"))
        store.validate(second.snapshot_id)
        store.activate(second.snapshot_id)
        self.assertEqual(domain_id, store.find_requirements()[0]["domain_id"])

    def test_sqlite_store_preserves_uuidv7_across_reopen_and_new_snapshot(self) -> None:
        domain_id = canonical(generate_uuid7(now_ms=RFC_SAMPLE_TS_MS, random_bits=2))
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "identity.sqlite"
            store = SQLiteSpecificationKnowledgeStore(path)
            first = store.begin_snapshot(source_revision="r1", payload=payload(domain_id, "v1"))
            store.validate(first)
            store.activate(first)
            store.close()

            store = SQLiteSpecificationKnowledgeStore(path)
            try:
                self.assertEqual(domain_id, store.find_requirements()[0]["domain_id"])
                second = store.begin_snapshot(source_revision="r2", payload=payload(domain_id, "v2"))
                store.validate(second)
                store.activate(second)
                self.assertEqual(domain_id, store.find_requirements()[0]["domain_id"])
            finally:
                store.close()

    def test_uuidv7_contains_no_provider_or_locator_material(self) -> None:
        value = canonical(generate_uuid7(now_ms=RFC_SAMPLE_TS_MS, random_bits=3))
        self.assertNotIn("openspec", value)
        self.assertNotIn("spec.md", value)
        self.assertNotIn("requirement", value)


if __name__ == "__main__":
    unittest.main()
