from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Protocol


@dataclass
class ExternalReference:
    system: str
    project: str
    resource_type: str
    external_id: str
    revision: str | None = None
    state: str = "UNVALIDATED"
    provenance: str | None = None
    resolved_payload: dict[str, Any] | None = None
    history: list[dict[str, Any]] = field(default_factory=list)

    def record(self, *, state: str, reason: str, payload: dict[str, Any] | None = None) -> None:
        self.history.append(
            {
                "from": self.state,
                "to": state,
                "reason": reason,
                "revision": self.revision,
            }
        )
        self.state = state
        self.resolved_payload = payload


class ExternalResolver(Protocol):
    system: str

    def resolve(self, reference: ExternalReference) -> dict[str, Any] | None:
        ...


class ResolverRegistry:
    def __init__(self) -> None:
        self._resolvers: dict[str, ExternalResolver] = {}

    def register(self, resolver: ExternalResolver) -> None:
        self._resolvers[resolver.system] = resolver

    def resolve(self, reference: ExternalReference) -> ExternalReference:
        resolver = self._resolvers.get(reference.system)
        if resolver is None:
            target_state = "STALE" if reference.state == "RESOLVED" else "UNRESOLVED"
            reference.record(state=target_state, reason="NO_RESOLVER")
            return reference

        payload = resolver.resolve(reference)
        if payload is None:
            target_state = "STALE" if reference.state == "RESOLVED" else "UNRESOLVED"
            reference.record(state=target_state, reason="TARGET_NOT_FOUND")
            return reference

        reference.record(state="RESOLVED", reason="TARGET_RESOLVED", payload=payload)
        return reference


class FakeMinosResolver:
    system = "MINOS"

    def __init__(self, resources: dict[tuple[str, str, str], dict[str, Any]] | None = None) -> None:
        self.resources = resources or {}

    def put(
        self,
        *,
        project: str,
        resource_type: str,
        external_id: str,
        payload: dict[str, Any],
    ) -> None:
        self.resources[(project, resource_type, external_id)] = payload

    def remove(self, *, project: str, resource_type: str, external_id: str) -> None:
        self.resources.pop((project, resource_type, external_id), None)

    def resolve(self, reference: ExternalReference) -> dict[str, Any] | None:
        return self.resources.get(
            (reference.project, reference.resource_type, reference.external_id)
        )
