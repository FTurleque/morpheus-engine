from __future__ import annotations

import json
from typing import Any, Iterable


class ChangeNotFound(KeyError):
    pass


def _compact_requirement(requirement: dict[str, Any]) -> dict[str, Any]:
    return {
        "key": requirement.get("key"),
        "title": requirement.get("title"),
        "statement": requirement.get("statement"),
        "delta_kind": requirement.get("delta_kind"),
        "provenance": requirement.get("provenance"),
    }


def _compact_items(items: Iterable[dict[str, Any]], fields: tuple[str, ...]) -> list[dict[str, Any]]:
    return [{field: item.get(field) for field in fields if field in item} for item in items]


def build_change_context(
    payload: dict[str, Any],
    change_key: str,
    *,
    provider_capabilities: Iterable[str] = (),
    task_limit: int = 20,
) -> dict[str, Any]:
    change = next(
        (
            item
            for item in payload.get("proposed", {}).get("changes", [])
            if item.get("key") == change_key
        ),
        None,
    )
    if change is None:
        raise ChangeNotFound(change_key)

    capabilities = set(provider_capabilities)
    requirements = [
        _compact_requirement(requirement)
        for requirement in change.get("requirements", [])
    ]
    constraints = _compact_items(
        change.get("constraints", []),
        ("text", "provenance"),
    )
    decisions = _compact_items(
        change.get("design_decisions", []),
        ("title", "statement", "provenance"),
    )
    tasks = _compact_items(
        change.get("tasks", [])[: max(task_limit, 0)],
        ("label", "completed", "provenance"),
    )

    acceptance_criteria: list[dict[str, Any]] = []
    capability_gaps: list[str] = []
    if "READ_ACCEPTANCE_CRITERIA" not in capabilities:
        capability_gaps.append("READ_ACCEPTANCE_CRITERIA")
    else:
        acceptance_criteria = _compact_items(
            change.get("acceptance_criteria", []),
            ("key", "statement", "verification_status", "provenance"),
        )

    traceability = [
        {
            "source": change_key,
            "relation": "AFFECTS",
            "target": requirement.get("key"),
            "origin": "DERIVED",
            "evidence": requirement.get("provenance"),
        }
        for requirement in change.get("requirements", [])
        if requirement.get("key")
    ]

    proposal = change.get("proposal") or {}
    provenance: list[dict[str, Any]] = []
    for item in [proposal, *change.get("constraints", []), *change.get("design_decisions", []), *change.get("tasks", [])]:
        location = item.get("provenance") if isinstance(item, dict) else None
        if location and location not in provenance:
            provenance.append(location)

    return {
        "change": change_key,
        "temporal_state": change.get("temporal_state"),
        "objective": proposal.get("intent"),
        "requirements": requirements,
        "constraints": constraints,
        "decisions": decisions,
        "acceptance_criteria": acceptance_criteria,
        "tasks": tasks,
        "traceability": traceability,
        "provenance": provenance,
        "capability_gaps": sorted(capability_gaps),
    }


def to_compact_json(context: dict[str, Any]) -> str:
    return json.dumps(
        context,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
    )
