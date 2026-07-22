from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from typing import Any

DIAGNOSTIC_CATALOG: dict[str, str] = {
    "NO_PROVIDER_FOUND": "ERROR",
    "UNSUPPORTED_SOURCE": "ERROR",
    "UNSUPPORTED_PROVIDER_SCHEMA": "ERROR",
    "UNSUPPORTED_FORMAT_VERSION": "ERROR",
    "MISSING_REQUIRED_CAPABILITY": "ERROR",
    "OPTIONAL_CAPABILITY_UNAVAILABLE": "WARNING",
    "MULTIPLE_PROVIDER_MATCHES": "WARNING",
    "EXPLICIT_PROVIDER_INCOMPATIBLE": "ERROR",
    "REMOTE_PROVIDER_REQUIRES_OPT_IN": "ERROR",
    "IDENTITY_COLLISION": "WARNING",
    "UNRESOLVED_REFERENCE": "WARNING",
    "INVALID_SOURCE": "ERROR",
    "PARTIAL_INGESTION": "WARNING",
    "SNAPSHOT_CONFLICT": "ERROR",
    "INVALID_SNAPSHOT": "ERROR",
}


@dataclass(frozen=True)
class Diagnostic:
    code: str
    message: str
    source: str | None = None
    details: dict[str, Any] = field(default_factory=dict)
    severity: str | None = None

    def __post_init__(self) -> None:
        if self.code not in DIAGNOSTIC_CATALOG:
            raise ValueError(f"unknown diagnostic code: {self.code}")
        expected = DIAGNOSTIC_CATALOG[self.code]
        if self.severity is None:
            object.__setattr__(self, "severity", expected)
        elif self.severity != expected:
            raise ValueError(
                f"severity {self.severity!r} does not match catalog severity {expected!r}"
            )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    def to_json(self) -> str:
        return json.dumps(
            self.to_dict(),
            sort_keys=True,
            separators=(",", ":"),
            ensure_ascii=False,
        )


def create_diagnostic(
    code: str,
    message: str,
    *,
    source: str | None = None,
    details: dict[str, Any] | None = None,
) -> Diagnostic:
    return Diagnostic(
        code=code,
        message=message,
        source=source,
        details=details or {},
    )
