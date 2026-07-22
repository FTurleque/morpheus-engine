from __future__ import annotations

import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Callable, Iterable

from spike import normalize as normalize_openspec
from spike import probe as probe_openspec

ProbeFn = Callable[[Path], dict[str, Any]]
NormalizeFn = Callable[[Path], dict[str, Any]]


@dataclass(frozen=True)
class ProviderAdapter:
    provider_id: str
    remote: bool
    probe_fn: ProbeFn
    normalize_fn: NormalizeFn


def _synthetic_probe(project_root: Path) -> dict[str, Any]:
    source = project_root / "morpheus-spec.json"
    if not source.exists():
        return {
            "provider": None,
            "schema": None,
            "format_version": None,
            "provider_contract_version": "m0-synthetic-v1",
            "supported": False,
            "capabilities": [],
            "diagnostics": ["NO_PROVIDER_FOUND"],
        }
    payload = json.loads(source.read_text(encoding="utf-8"))
    return {
        "provider": "synthetic-json",
        "schema": "morpheus-synthetic",
        "format_version": str(payload.get("format_version", "1")),
        "provider_contract_version": "m0-synthetic-v1",
        "supported": True,
        "capabilities": sorted(
            set(
                payload.get(
                    "capabilities",
                    [
                        "DISCOVER_PROJECT",
                        "READ_CURRENT_SPECIFICATIONS",
                        "READ_CHANGES",
                        "READ_REQUIREMENTS",
                        "READ_SCENARIOS",
                    ],
                )
            )
        ),
        "diagnostics": [],
    }


def _synthetic_normalize(project_root: Path) -> dict[str, Any]:
    source = project_root / "morpheus-spec.json"
    probe_result = _synthetic_probe(project_root)
    if not probe_result["supported"]:
        return {
            "probe": probe_result,
            "current": {"specifications": 0, "requirements": []},
            "proposed": {"changes": []},
            "historical": {"changes": []},
            "diagnostics": list(probe_result["diagnostics"]),
        }
    payload = json.loads(source.read_text(encoding="utf-8"))
    return {
        "probe": probe_result,
        "current": payload.get("current", {"specifications": 0, "requirements": []}),
        "proposed": payload.get("proposed", {"changes": []}),
        "historical": payload.get("historical", {"changes": []}),
        "diagnostics": payload.get("diagnostics", []),
    }


def openspec_adapter(*, remote: bool = False) -> ProviderAdapter:
    return ProviderAdapter("openspec", remote, probe_openspec, normalize_openspec)


def synthetic_adapter(*, remote: bool = False, provider_id: str = "synthetic-json") -> ProviderAdapter:
    base_probe = _synthetic_probe

    def probe_with_id(project_root: Path) -> dict[str, Any]:
        result = dict(base_probe(project_root))
        if result["supported"]:
            result["provider"] = provider_id
        return result

    return ProviderAdapter(provider_id, remote, probe_with_id, _synthetic_normalize)


def fixed_adapter(
    provider_id: str,
    *,
    capabilities: Iterable[str],
    remote: bool = False,
    supported: bool = True,
) -> ProviderAdapter:
    capability_list = sorted(set(capabilities))

    def probe_fn(project_root: Path) -> dict[str, Any]:
        del project_root
        return {
            "provider": provider_id if supported else None,
            "schema": "fixed",
            "format_version": "1",
            "provider_contract_version": "m0-fixed-v1",
            "supported": supported,
            "capabilities": capability_list if supported else [],
            "diagnostics": [] if supported else ["UNSUPPORTED_SOURCE"],
        }

    def normalize_fn(project_root: Path) -> dict[str, Any]:
        del project_root
        return {
            "probe": probe_fn(Path(".")),
            "current": {"specifications": 0, "requirements": []},
            "proposed": {"changes": []},
            "historical": {"changes": []},
            "diagnostics": [],
        }

    return ProviderAdapter(provider_id, remote, probe_fn, normalize_fn)


def select_provider(
    project_root: Path,
    candidates: Iterable[ProviderAdapter],
    *,
    required_capabilities: Iterable[str],
    preferred_capabilities: Iterable[str] = (),
    explicit_provider: str | None = None,
    allow_remote: bool = False,
) -> dict[str, Any]:
    required = set(required_capabilities)
    preferred = set(preferred_capabilities)
    probes: list[tuple[ProviderAdapter, dict[str, Any]]] = [
        (adapter, adapter.probe_fn(project_root)) for adapter in candidates
    ]

    if explicit_provider is not None:
        explicit = [item for item in probes if item[0].provider_id == explicit_provider]
        if not explicit or not explicit[0][1]["supported"]:
            return {
                "selected": None,
                "diagnostics": ["EXPLICIT_PROVIDER_INCOMPATIBLE"],
                "candidates": [probe for _, probe in probes],
            }
        probes = explicit

    remote_supported = [
        (adapter, probe)
        for adapter, probe in probes
        if probe["supported"] and adapter.remote
    ]
    if not allow_remote:
        probes = [
            (adapter, probe)
            for adapter, probe in probes
            if probe["supported"] and not adapter.remote
        ]
        if not probes and remote_supported:
            return {
                "selected": None,
                "diagnostics": ["REMOTE_PROVIDER_REQUIRES_OPT_IN"],
                "candidates": [probe for _, probe in remote_supported],
            }
    else:
        probes = [(adapter, probe) for adapter, probe in probes if probe["supported"]]

    if not probes:
        return {"selected": None, "diagnostics": ["NO_PROVIDER_FOUND"], "candidates": []}

    capable: list[tuple[ProviderAdapter, dict[str, Any]]] = []
    for adapter, probe in probes:
        capabilities = set(probe["capabilities"])
        if required.issubset(capabilities):
            capable.append((adapter, probe))

    if not capable:
        return {
            "selected": None,
            "diagnostics": ["MISSING_REQUIRED_CAPABILITY"],
            "candidates": [probe for _, probe in probes],
        }

    def score(item: tuple[ProviderAdapter, dict[str, Any]]) -> tuple[int, int, str]:
        adapter, probe = item
        optional_coverage = len(preferred.intersection(probe["capabilities"]))
        return (-optional_coverage, 1 if adapter.remote else 0, adapter.provider_id)

    capable.sort(key=score)
    adapter, selected_probe = capable[0]
    diagnostics: list[str] = []
    if len(capable) > 1:
        diagnostics.append("MULTIPLE_PROVIDER_MATCHES")
    missing_preferred = preferred.difference(selected_probe["capabilities"])
    if missing_preferred:
        diagnostics.append("OPTIONAL_CAPABILITY_UNAVAILABLE")

    return {
        "selected": adapter.provider_id,
        "remote": adapter.remote,
        "capabilities": selected_probe["capabilities"],
        "missing_preferred_capabilities": sorted(missing_preferred),
        "diagnostics": diagnostics,
        "candidates": [probe for _, probe in capable],
    }


def normalize_with_provider(project_root: Path, adapter: ProviderAdapter) -> dict[str, Any]:
    return adapter.normalize_fn(project_root)
