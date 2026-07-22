from __future__ import annotations

import hashlib
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Observation:
    provider_id: str
    kind: str
    locator: str
    title: str
    statement: str
    external_id: str | None = None
    logical_key: str | None = None
    previous_external_id: str | None = None
    temporal_state: str = "CURRENT"


@dataclass
class IdentityRecord:
    domain_id: str
    provider_id: str
    kind: str
    external_ids: set[str] = field(default_factory=set)
    logical_keys: set[str] = field(default_factory=set)
    locators: list[str] = field(default_factory=list)
    title: str = ""
    statement: str = ""
    content_fingerprint: str = ""
    temporal_state: str = "CURRENT"
    active: bool = True


@dataclass(frozen=True)
class ResolveResult:
    domain_id: str
    resolution: str
    reason: str
    warnings: tuple[str, ...] = ()


class IdentityRegistry:
    """Disposable E03 registry. IDs are intentionally experimental, not product format."""

    def __init__(self) -> None:
        self._next_id = 1
        self.records: dict[str, IdentityRecord] = {}
        self.by_external: dict[tuple[str, str, str], str] = {}
        self.by_logical_key: dict[tuple[str, str, str], str] = {}

    @staticmethod
    def _fingerprint(observation: Observation) -> str:
        normalized = " ".join(observation.statement.lower().split())
        return hashlib.sha256(normalized.encode("utf-8")).hexdigest()

    def _allocate(self) -> str:
        value = f"exp-{self._next_id:06d}"
        self._next_id += 1
        return value

    def _register_aliases(self, record: IdentityRecord, observation: Observation) -> list[str]:
        warnings: list[str] = []
        if observation.external_id:
            key = (observation.provider_id, observation.kind, observation.external_id)
            owner = self.by_external.get(key)
            if owner is not None and owner != record.domain_id:
                warnings.append("IDENTITY_COLLISION")
            else:
                self.by_external[key] = record.domain_id
                record.external_ids.add(observation.external_id)

        if observation.logical_key:
            key = (observation.provider_id, observation.kind, observation.logical_key)
            owner = self.by_logical_key.get(key)
            if owner is not None and owner != record.domain_id:
                warnings.append("IDENTITY_COLLISION")
            else:
                self.by_logical_key[key] = record.domain_id
                record.logical_keys.add(observation.logical_key)
        return warnings

    def _update_record(self, record: IdentityRecord, observation: Observation) -> list[str]:
        warnings = self._register_aliases(record, observation)
        if not record.locators or record.locators[-1] != observation.locator:
            record.locators.append(observation.locator)
        record.title = observation.title
        record.statement = observation.statement
        record.content_fingerprint = self._fingerprint(observation)
        record.temporal_state = observation.temporal_state
        record.active = observation.temporal_state != "HISTORICAL"
        return warnings

    def resolve(self, observation: Observation) -> ResolveResult:
        if observation.external_id:
            key = (observation.provider_id, observation.kind, observation.external_id)
            existing = self.by_external.get(key)
            if existing:
                record = self.records[existing]
                warnings = self._update_record(record, observation)
                return ResolveResult(existing, "RESOLVED", "EXTERNAL_ID", tuple(warnings))

        if observation.previous_external_id:
            previous_key = (
                observation.provider_id,
                observation.kind,
                observation.previous_external_id,
            )
            existing = self.by_external.get(previous_key)
            if existing:
                record = self.records[existing]
                warnings = self._update_record(record, observation)
                return ResolveResult(
                    existing,
                    "RESOLVED",
                    "EXPLICIT_CONTINUITY",
                    tuple(warnings),
                )

        if observation.logical_key:
            key = (observation.provider_id, observation.kind, observation.logical_key)
            existing = self.by_logical_key.get(key)
            if existing:
                if observation.external_id and observation.external_id not in self.records[existing].external_ids:
                    domain_id = self._allocate()
                    record = IdentityRecord(
                        domain_id=domain_id,
                        provider_id=observation.provider_id,
                        kind=observation.kind,
                    )
                    warnings = self._update_record(record, observation)
                    if "IDENTITY_COLLISION" not in warnings:
                        warnings.append("IDENTITY_COLLISION")
                    self.records[domain_id] = record
                    return ResolveResult(
                        domain_id,
                        "PARTIALLY_RESOLVED",
                        "CONFLICTING_IDENTIFIERS",
                        tuple(warnings),
                    )
                record = self.records[existing]
                warnings = self._update_record(record, observation)
                return ResolveResult(existing, "RESOLVED", "LOGICAL_KEY", tuple(warnings))

        fingerprint = self._fingerprint(observation)
        candidates = [
            record
            for record in self.records.values()
            if record.provider_id == observation.provider_id
            and record.kind == observation.kind
            and record.content_fingerprint == fingerprint
        ]

        domain_id = self._allocate()
        record = IdentityRecord(
            domain_id=domain_id,
            provider_id=observation.provider_id,
            kind=observation.kind,
        )
        warnings = self._update_record(record, observation)
        self.records[domain_id] = record

        if candidates:
            warnings.append("HEURISTIC_CONTINUITY_CANDIDATE")
            return ResolveResult(
                domain_id,
                "HEURISTIC",
                "NEW_IDENTITY_WITH_SIMILAR_CONTENT",
                tuple(warnings),
            )
        return ResolveResult(domain_id, "RESOLVED", "NEW_IDENTITY", tuple(warnings))

    def delete(self, domain_id: str) -> None:
        self.records[domain_id].active = False

    def archive(self, domain_id: str) -> None:
        record = self.records[domain_id]
        record.temporal_state = "HISTORICAL"
        record.active = False

    def get(self, domain_id: str) -> IdentityRecord:
        return self.records[domain_id]
