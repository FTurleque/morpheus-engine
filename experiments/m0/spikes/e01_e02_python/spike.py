from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

SUPPORTED_SCHEMAS = {"spec-driven"}
PROVIDER_CONTRACT_VERSION = "m0-e01-e02-v2"

REQ_RE = re.compile(r"^### Requirement:\s*(.+?)\s*$")
SCENARIO_RE = re.compile(r"^#### Scenario:\s*(.+?)\s*$")
TASK_RE = re.compile(r"^\s*- \[(?P<state>[ xX])\]\s+(?P<label>.+?)\s*$")
SECTION_RE = re.compile(r"^##\s+(ADDED|MODIFIED|REMOVED) Requirements\s*$")


@dataclass(frozen=True)
class SourceLocation:
    path: str
    line: int


@dataclass
class Requirement:
    key: str
    title: str
    statement: str
    temporal_state: str
    change: str | None
    delta_kind: str | None
    scenarios: list[dict[str, Any]]
    provenance: SourceLocation


@dataclass
class Change:
    key: str
    temporal_state: str
    proposal: dict[str, Any]
    design_decisions: list[dict[str, Any]]
    tasks: list[dict[str, Any]]
    requirements: list[Requirement]
    constraints: list[dict[str, Any]]


def _rel(path: Path, root: Path) -> str:
    return path.relative_to(root).as_posix()


def read_schema(config: Path) -> str | None:
    """Read the schema key needed by E01 without introducing a YAML dependency."""
    if not config.exists():
        return None
    for line in config.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^schema:\s*([^#\s]+)", line)
        if match:
            return match.group(1).strip()
    return None


def _section_lines(path: Path, section_name: str) -> tuple[list[tuple[int, str]], SourceLocation | None]:
    if not path.exists():
        return [], None
    lines = path.read_text(encoding="utf-8").splitlines()
    target = f"## {section_name}"
    start: int | None = None
    collected: list[tuple[int, str]] = []
    for index, line in enumerate(lines):
        if line.strip() == target:
            start = index + 1
            continue
        if start is not None:
            if line.startswith("## "):
                break
            if line.strip():
                collected.append((index + 1, line.strip()))
    return collected, (SourceLocation(str(path), start) if start is not None else None)


def _bullets(lines: list[tuple[int, str]], path: Path, root: Path) -> list[dict[str, Any]]:
    result: list[dict[str, Any]] = []
    for line_no, text in lines:
        if text.startswith("- "):
            result.append(
                {
                    "text": text[2:].strip(),
                    "provenance": {"path": _rel(path, root), "line": line_no},
                }
            )
    return result


def parse_proposal(path: Path, project_root: Path) -> dict[str, Any]:
    if not path.exists():
        return {
            "intent": None,
            "scope": [],
            "out_of_scope": [],
            "risks": [],
            "provenance": None,
        }

    intent_lines, _ = _section_lines(path, "Intent")
    scope_lines, _ = _section_lines(path, "Scope")
    out_lines, _ = _section_lines(path, "Out of scope")
    risk_lines, _ = _section_lines(path, "Risks")

    intent = " ".join(text for _, text in intent_lines).strip() or None
    return {
        "intent": intent,
        "scope": _bullets(scope_lines, path, project_root),
        "out_of_scope": _bullets(out_lines, path, project_root),
        "risks": _bullets(risk_lines, path, project_root),
        "provenance": {"path": _rel(path, project_root), "line": 1},
    }


def parse_constraints(path: Path, project_root: Path) -> list[dict[str, Any]]:
    lines, _ = _section_lines(path, "Constraints")
    return _bullets(lines, path, project_root)


def parse_design_decisions(path: Path, project_root: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    in_decisions = False
    result: list[dict[str, Any]] = []
    index = 0
    while index < len(lines):
        line = lines[index]
        if line.strip() == "## Decisions":
            in_decisions = True
            index += 1
            continue
        if in_decisions and line.startswith("## ") and line.strip() != "## Decisions":
            break
        if in_decisions and line.startswith("### "):
            title = line[4:].strip()
            line_no = index + 1
            body: list[str] = []
            index += 1
            while index < len(lines) and not lines[index].startswith("### ") and not lines[index].startswith("## "):
                stripped = lines[index].strip()
                if stripped:
                    body.append(stripped)
                index += 1
            result.append(
                {
                    "title": title,
                    "statement": " ".join(body),
                    "provenance": {"path": _rel(path, project_root), "line": line_no},
                }
            )
            continue
        index += 1
    return result


def parse_tasks(path: Path, project_root: Path) -> list[dict[str, Any]]:
    if not path.exists():
        return []
    result: list[dict[str, Any]] = []
    for index, line in enumerate(path.read_text(encoding="utf-8").splitlines(), start=1):
        match = TASK_RE.match(line)
        if match:
            result.append(
                {
                    "label": match.group("label").strip(),
                    "completed": match.group("state").lower() == "x",
                    "provenance": {"path": _rel(path, project_root), "line": index},
                }
            )
    return result


def parse_requirements(
    file_path: Path,
    project_root: Path,
    *,
    spec_key: str,
    temporal_state: str,
    change: str | None = None,
    delta_mode: bool = False,
) -> list[Requirement]:
    lines = file_path.read_text(encoding="utf-8").splitlines()
    result: list[Requirement] = []
    current_delta: str | None = None
    index = 0

    while index < len(lines):
        line = lines[index]
        if delta_mode:
            section = SECTION_RE.match(line)
            if section:
                current_delta = section.group(1)
                index += 1
                continue

        req_match = REQ_RE.match(line)
        if not req_match:
            index += 1
            continue

        title = req_match.group(1).strip()
        req_line = index + 1
        statement_lines: list[str] = []
        scenarios: list[dict[str, Any]] = []
        index += 1

        while index < len(lines):
            next_line = lines[index]
            if REQ_RE.match(next_line) or (delta_mode and SECTION_RE.match(next_line)):
                break

            scenario_match = SCENARIO_RE.match(next_line)
            if scenario_match:
                scenario_title = scenario_match.group(1).strip()
                scenario_line = index + 1
                steps: list[str] = []
                index += 1
                while index < len(lines):
                    scenario_line_text = lines[index]
                    if (
                        REQ_RE.match(scenario_line_text)
                        or SCENARIO_RE.match(scenario_line_text)
                        or (delta_mode and SECTION_RE.match(scenario_line_text))
                    ):
                        break
                    stripped = scenario_line_text.strip()
                    if stripped:
                        steps.append(stripped)
                    index += 1
                scenarios.append(
                    {
                        "title": scenario_title,
                        "steps": steps,
                        "provenance": {
                            "path": _rel(file_path, project_root),
                            "line": scenario_line,
                        },
                    }
                )
                continue

            stripped = next_line.strip()
            if stripped and not stripped.startswith("#"):
                statement_lines.append(stripped)
            index += 1

        slug = re.sub(r"[^a-z0-9]+", "-", title.lower()).strip("-")
        result.append(
            Requirement(
                key=f"{spec_key}/{slug}",
                title=title,
                statement=" ".join(statement_lines).strip(),
                temporal_state=temporal_state,
                change=change,
                delta_kind=current_delta if delta_mode else None,
                scenarios=scenarios,
                provenance=SourceLocation(_rel(file_path, project_root), req_line),
            )
        )

    return result


def probe(project_root: Path) -> dict[str, Any]:
    openspec = project_root / "openspec"
    config = openspec / "config.yaml"
    if not openspec.exists() or not config.exists():
        return {
            "provider": None,
            "schema": None,
            "format_version": None,
            "provider_contract_version": PROVIDER_CONTRACT_VERSION,
            "supported": False,
            "capabilities": [],
            "diagnostics": ["NO_PROVIDER_FOUND"],
        }

    schema = read_schema(config)
    if schema not in SUPPORTED_SCHEMAS:
        return {
            "provider": "openspec",
            "schema": schema,
            "format_version": None,
            "provider_contract_version": PROVIDER_CONTRACT_VERSION,
            "supported": False,
            "capabilities": [],
            "diagnostics": ["UNSUPPORTED_PROVIDER_SCHEMA"],
        }

    capabilities: list[str] = ["DISCOVER_PROJECT"]
    if (openspec / "specs").exists():
        capabilities.extend(
            [
                "READ_CURRENT_SPECIFICATIONS",
                "READ_REQUIREMENTS",
                "READ_SCENARIOS",
            ]
        )
    if (openspec / "changes").exists():
        capabilities.extend(
            [
                "READ_CHANGES",
                "READ_DESIGN_DECISIONS",
                "READ_IMPLEMENTATION_TASKS",
            ]
        )
    if (openspec / "changes" / "archive").exists():
        capabilities.extend(["READ_HISTORY", "READ_ARCHIVES"])

    return {
        "provider": "openspec",
        "schema": schema,
        # OpenSpec project config identifies a schema, not an independent format version.
        "format_version": None,
        "provider_contract_version": PROVIDER_CONTRACT_VERSION,
        "supported": True,
        "capabilities": sorted(set(capabilities)),
        "diagnostics": [],
    }


def _parse_change(change_dir: Path, project_root: Path, temporal_state: str) -> Change:
    change_requirements: list[Requirement] = []
    change_specs = change_dir / "specs"
    if change_specs.exists():
        for spec_file in sorted(change_specs.glob("*/spec.md")):
            spec_key = spec_file.parent.name
            change_requirements.extend(
                parse_requirements(
                    spec_file,
                    project_root,
                    spec_key=spec_key,
                    temporal_state=temporal_state,
                    change=change_dir.name,
                    delta_mode=True,
                )
            )

    proposal_path = change_dir / "proposal.md"
    design_path = change_dir / "design.md"
    tasks_path = change_dir / "tasks.md"
    return Change(
        key=change_dir.name,
        temporal_state=temporal_state,
        proposal=parse_proposal(proposal_path, project_root),
        design_decisions=parse_design_decisions(design_path, project_root),
        tasks=parse_tasks(tasks_path, project_root),
        requirements=change_requirements,
        constraints=parse_constraints(proposal_path, project_root),
    )


def normalize(project_root: Path) -> dict[str, Any]:
    probe_result = probe(project_root)
    if not probe_result["supported"]:
        return {
            "probe": probe_result,
            "current": {"specifications": 0, "requirements": []},
            "proposed": {"changes": []},
            "historical": {"changes": []},
            "diagnostics": list(probe_result["diagnostics"]),
        }

    openspec = project_root / "openspec"
    diagnostics: list[str] = []

    current_requirements: list[Requirement] = []
    specs_root = openspec / "specs"
    if specs_root.exists():
        for spec_file in sorted(specs_root.glob("*/spec.md")):
            spec_key = spec_file.parent.name
            parsed = parse_requirements(
                spec_file,
                project_root,
                spec_key=spec_key,
                temporal_state="CURRENT",
            )
            if not parsed:
                diagnostics.append("INVALID_SOURCE")
            current_requirements.extend(parsed)

    changes: list[Change] = []
    changes_root = openspec / "changes"
    if changes_root.exists():
        for change_dir in sorted(
            path
            for path in changes_root.iterdir()
            if path.is_dir() and path.name != "archive"
        ):
            changes.append(_parse_change(change_dir, project_root, "PROPOSED"))

    historical_changes: list[Change] = []
    archive_root = changes_root / "archive"
    if archive_root.exists():
        for change_dir in sorted(path for path in archive_root.iterdir() if path.is_dir()):
            historical_changes.append(_parse_change(change_dir, project_root, "HISTORICAL"))

    all_requirements = list(current_requirements)
    for change in [*changes, *historical_changes]:
        all_requirements.extend(change.requirements)
    if any(not requirement.scenarios for requirement in all_requirements):
        diagnostics.append("PARTIAL_INGESTION")

    def serialize_change(change: Change) -> dict[str, Any]:
        raw = asdict(change)
        raw["requirements"] = [asdict(requirement) for requirement in change.requirements]
        return raw

    return {
        "probe": probe_result,
        "current": {
            "specifications": len({r.key.split("/")[0] for r in current_requirements}),
            "requirements": [asdict(r) for r in current_requirements],
        },
        "proposed": {"changes": [serialize_change(change) for change in changes]},
        "historical": {
            "changes": [serialize_change(change) for change in historical_changes]
        },
        "diagnostics": sorted(set(diagnostics)),
    }


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Disposable MORPHEUS M0 E01/E02 spike"
    )
    parser.add_argument("project", type=Path)
    args = parser.parse_args()

    result = normalize(args.project.resolve())
    print(json.dumps(result, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
