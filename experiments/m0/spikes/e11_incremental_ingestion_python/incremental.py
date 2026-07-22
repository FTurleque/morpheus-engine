from __future__ import annotations

import hashlib
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable


@dataclass(frozen=True)
class FileState:
    path: str
    sha256: str
    size: int


@dataclass(frozen=True)
class InventoryDiff:
    added: tuple[str, ...]
    modified: tuple[str, ...]
    removed: tuple[str, ...]
    renamed: tuple[tuple[str, str], ...]
    unchanged: tuple[str, ...]


def inventory(root: Path, *, suffixes: Iterable[str] = (".md", ".yaml", ".json")) -> dict[str, FileState]:
    allowed = set(suffixes)
    result: dict[str, FileState] = {}
    for path in sorted(item for item in root.rglob("*") if item.is_file()):
        if path.suffix not in allowed:
            continue
        rel = path.relative_to(root).as_posix()
        data = path.read_bytes()
        result[rel] = FileState(
            path=rel,
            sha256=hashlib.sha256(data).hexdigest(),
            size=len(data),
        )
    return result


def diff(old: dict[str, FileState], new: dict[str, FileState]) -> InventoryDiff:
    old_paths = set(old)
    new_paths = set(new)

    unchanged = {
        path
        for path in old_paths.intersection(new_paths)
        if old[path].sha256 == new[path].sha256
    }
    modified = old_paths.intersection(new_paths) - unchanged
    removed = set(old_paths - new_paths)
    added = set(new_paths - old_paths)

    removed_by_hash: dict[str, list[str]] = {}
    added_by_hash: dict[str, list[str]] = {}
    for path in removed:
        removed_by_hash.setdefault(old[path].sha256, []).append(path)
    for path in added:
        added_by_hash.setdefault(new[path].sha256, []).append(path)

    renames: list[tuple[str, str]] = []
    for sha in sorted(set(removed_by_hash).intersection(added_by_hash)):
        olds = sorted(removed_by_hash[sha])
        news = sorted(added_by_hash[sha])
        if len(olds) == 1 and len(news) == 1:
            renames.append((olds[0], news[0]))
            removed.remove(olds[0])
            added.remove(news[0])

    return InventoryDiff(
        added=tuple(sorted(added)),
        modified=tuple(sorted(modified)),
        removed=tuple(sorted(removed)),
        renamed=tuple(renames),
        unchanged=tuple(sorted(unchanged)),
    )


def affected_entities(change: InventoryDiff, provenance_index: dict[str, set[str]]) -> set[str]:
    changed_paths = set(change.added) | set(change.modified) | set(change.removed)
    for old_path, new_path in change.renamed:
        changed_paths.add(old_path)
        changed_paths.add(new_path)

    affected: set[str] = set()
    for path in changed_paths:
        affected.update(provenance_index.get(path, set()))
    return affected
