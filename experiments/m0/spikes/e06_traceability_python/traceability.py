from __future__ import annotations

from collections import deque
from dataclasses import dataclass
from typing import Iterable

ALLOWED_RELATIONS = {
    "REFINES",
    "DERIVES_FROM",
    "CONSTRAINS",
    "IMPLEMENTS",
    "SATISFIES",
    "VALIDATES",
    "VERIFIED_BY",
    "DECIDED_BY",
    "DEPENDS_ON",
    "AFFECTS",
    "SUPERSEDES",
    "LINKS_TO_CODE",
    "LINKS_TO_TEST",
    "RELATED_TO",
}

ALLOWED_ORIGINS = {"EXPLICIT", "DERIVED", "HEURISTIC"}
ALLOWED_RESOLUTIONS = {"RESOLVED", "PARTIALLY_RESOLVED", "UNRESOLVED", "HEURISTIC"}


@dataclass(frozen=True)
class TraceabilityLink:
    source: str
    relation_type: str
    target: str
    origin: str
    resolution: str
    confidence: float | None = None
    evidence: str | None = None

    def __post_init__(self) -> None:
        if self.relation_type not in ALLOWED_RELATIONS:
            raise ValueError(f"unsupported relation: {self.relation_type}")
        if self.origin not in ALLOWED_ORIGINS:
            raise ValueError(f"unsupported origin: {self.origin}")
        if self.resolution not in ALLOWED_RESOLUTIONS:
            raise ValueError(f"unsupported resolution: {self.resolution}")
        if self.confidence is not None and not 0.0 <= self.confidence <= 1.0:
            raise ValueError("confidence must be between 0 and 1")


class TraceabilityGraph:
    def __init__(self) -> None:
        self.links: list[TraceabilityLink] = []

    def add(self, link: TraceabilityLink) -> None:
        if link not in self.links:
            self.links.append(link)

    def outgoing(self, source: str, relation_type: str | None = None) -> list[TraceabilityLink]:
        return [
            link
            for link in self.links
            if link.source == source
            and (relation_type is None or link.relation_type == relation_type)
        ]

    def incoming(self, target: str, relation_type: str | None = None) -> list[TraceabilityLink]:
        return [
            link
            for link in self.links
            if link.target == target
            and (relation_type is None or link.relation_type == relation_type)
        ]

    def traverse(
        self,
        start: str,
        *,
        max_depth: int,
        relation_types: Iterable[str] | None = None,
        bidirectional: bool = False,
    ) -> list[dict]:
        allowed = set(relation_types) if relation_types is not None else None
        queue = deque([(start, 0, [start])])
        visited = {start}
        paths: list[dict] = []

        while queue:
            node, depth, path = queue.popleft()
            if depth >= max_depth:
                continue

            candidates = self.outgoing(node)
            if bidirectional:
                candidates += [
                    TraceabilityLink(
                        source=node,
                        relation_type=link.relation_type,
                        target=link.source,
                        origin=link.origin,
                        resolution=link.resolution,
                        confidence=link.confidence,
                        evidence=link.evidence,
                    )
                    for link in self.incoming(node)
                ]

            for link in candidates:
                if allowed is not None and link.relation_type not in allowed:
                    continue
                next_node = link.target
                new_path = [*path, next_node]
                paths.append(
                    {
                        "depth": depth + 1,
                        "path": new_path,
                        "relation": link.relation_type,
                        "origin": link.origin,
                        "resolution": link.resolution,
                        "evidence": link.evidence,
                    }
                )
                if next_node not in visited:
                    visited.add(next_node)
                    queue.append((next_node, depth + 1, new_path))
        return paths

    def unresolved(self) -> list[TraceabilityLink]:
        return [link for link in self.links if link.resolution == "UNRESOLVED"]
