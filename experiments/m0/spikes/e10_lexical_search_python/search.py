from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass(frozen=True)
class SearchResult:
    kind: str
    key: str
    title: str
    temporal_state: str
    score: int
    payload: dict[str, Any]


class LexicalSearchEngine:
    def __init__(self, payload: dict[str, Any]) -> None:
        self.items: list[dict[str, Any]] = []

        for requirement in payload.get("current", {}).get("requirements", []):
            self.items.append(
                {
                    "kind": "Requirement",
                    "key": requirement.get("key", ""),
                    "title": requirement.get("title", ""),
                    "text": requirement.get("statement", ""),
                    "temporal_state": requirement.get("temporal_state", "CURRENT"),
                    "payload": requirement,
                }
            )

        for change in payload.get("proposed", {}).get("changes", []):
            proposal = change.get("proposal", {})
            self.items.append(
                {
                    "kind": "ChangeProposal",
                    "key": change.get("key", ""),
                    "title": change.get("key", ""),
                    "text": proposal.get("intent") or "",
                    "temporal_state": change.get("temporal_state", "PROPOSED"),
                    "payload": change,
                }
            )
            for requirement in change.get("requirements", []):
                self.items.append(
                    {
                        "kind": "Requirement",
                        "key": requirement.get("key", ""),
                        "title": requirement.get("title", ""),
                        "text": requirement.get("statement", ""),
                        "temporal_state": requirement.get("temporal_state", "PROPOSED"),
                        "payload": requirement,
                    }
                )

        for change in payload.get("historical", {}).get("changes", []):
            self.items.append(
                {
                    "kind": "ChangeProposal",
                    "key": change.get("key", ""),
                    "title": change.get("key", ""),
                    "text": (change.get("proposal") or {}).get("intent") or "",
                    "temporal_state": change.get("temporal_state", "HISTORICAL"),
                    "payload": change,
                }
            )

    @staticmethod
    def _score(item: dict[str, Any], needle: str) -> int | None:
        key = item["key"].lower()
        title = item["title"].lower()
        text = item["text"].lower()
        if key == needle:
            return 0
        if title == needle:
            return 1
        if key.startswith(needle):
            return 2
        if title.startswith(needle):
            return 3
        if needle in key:
            return 4
        if needle in title:
            return 5
        if needle in text:
            return 6
        return None

    def search(
        self,
        query: str,
        *,
        kinds: set[str] | None = None,
        temporal_states: set[str] | None = None,
        limit: int = 20,
    ) -> list[SearchResult]:
        needle = " ".join(query.lower().split())
        if not needle or limit <= 0:
            return []

        results: list[SearchResult] = []
        for item in self.items:
            if kinds is not None and item["kind"] not in kinds:
                continue
            if temporal_states is not None and item["temporal_state"] not in temporal_states:
                continue
            score = self._score(item, needle)
            if score is None:
                continue
            results.append(
                SearchResult(
                    kind=item["kind"],
                    key=item["key"],
                    title=item["title"],
                    temporal_state=item["temporal_state"],
                    score=score,
                    payload=item["payload"],
                )
            )

        results.sort(
            key=lambda result: (
                result.score,
                result.kind,
                result.temporal_state,
                result.key,
                result.title,
            )
        )
        return results[:limit]
