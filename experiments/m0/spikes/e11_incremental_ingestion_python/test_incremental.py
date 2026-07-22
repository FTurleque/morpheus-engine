from __future__ import annotations

import tempfile
import unittest
from pathlib import Path

from incremental import affected_entities, diff, inventory


class IncrementalIngestionTest(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "a.md").write_text("A", encoding="utf-8")
        (self.root / "b.md").write_text("B", encoding="utf-8")
        (self.root / "ignored.txt").write_text("ignored", encoding="utf-8")
        self.before = inventory(self.root)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_unchanged_source(self) -> None:
        result = diff(self.before, inventory(self.root))
        self.assertEqual(("a.md", "b.md"), result.unchanged)
        self.assertEqual((), result.added)
        self.assertEqual((), result.modified)
        self.assertEqual((), result.removed)

    def test_add_file(self) -> None:
        (self.root / "c.md").write_text("C", encoding="utf-8")
        result = diff(self.before, inventory(self.root))
        self.assertEqual(("c.md",), result.added)

    def test_modify_file(self) -> None:
        (self.root / "a.md").write_text("A2", encoding="utf-8")
        result = diff(self.before, inventory(self.root))
        self.assertEqual(("a.md",), result.modified)

    def test_remove_file(self) -> None:
        (self.root / "b.md").unlink()
        result = diff(self.before, inventory(self.root))
        self.assertEqual(("b.md",), result.removed)

    def test_detect_exact_content_rename(self) -> None:
        (self.root / "a.md").rename(self.root / "renamed.md")
        result = diff(self.before, inventory(self.root))
        self.assertEqual((("a.md", "renamed.md"),), result.renamed)
        self.assertNotIn("a.md", result.removed)
        self.assertNotIn("renamed.md", result.added)

    def test_changed_content_move_is_not_silently_called_rename(self) -> None:
        content = (self.root / "a.md").read_text(encoding="utf-8")
        (self.root / "a.md").unlink()
        (self.root / "renamed.md").write_text(content + " changed", encoding="utf-8")
        result = diff(self.before, inventory(self.root))
        self.assertEqual((), result.renamed)
        self.assertEqual(("renamed.md",), result.added)
        self.assertEqual(("a.md",), result.removed)

    def test_invalidation_uses_provenance(self) -> None:
        (self.root / "a.md").write_text("A2", encoding="utf-8")
        result = diff(self.before, inventory(self.root))
        provenance = {
            "a.md": {"req:1", "scenario:1"},
            "b.md": {"req:2"},
        }
        self.assertEqual({"req:1", "scenario:1"}, affected_entities(result, provenance))

    def test_non_supported_suffix_is_ignored(self) -> None:
        (self.root / "ignored.txt").write_text("changed", encoding="utf-8")
        result = diff(self.before, inventory(self.root))
        self.assertEqual(("a.md", "b.md"), result.unchanged)


if __name__ == "__main__":
    unittest.main()
