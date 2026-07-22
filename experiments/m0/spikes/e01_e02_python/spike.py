from __future__ import annotations

import argparse
import json
import re
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

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
    proposal_path: str | None
    design_path: str | None
    tasks_path: str | None
    task_count: int
    design_decision_count: int
    requirements: list[Requirement]


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


def count_tasks(path: Path) -> int:
    if not path.exists():
        return 0
    return sum(
        1
        for line in path.read_text(encoding="utf-8").splitlines()
        if TASK_RE.match(line)
    )


def count_design_decisions(path: Path) -> int:
    if not path.exists():
        return 0

    in_decisions = False
    count = 0
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() == "## Decisions":
            in_decisions = True
            continue
        if in_decisions and line.startswith("## ") and line.strip() != "## Decisions":
            in_decisions = False
        if in_decisions and line.startswith("### "):
            count += 1
    return count


def probe(project_root: Path) -> dict[str, Any]:
    openspec = project_root / "openspec"
    config = openspec / "config.yaml"
    schema = read_schema(config)
    supported = bool(openspec.exists() and config.exists() and schema)

    capabilities: list[str] = []
    if supported:
        capabilities.append("DISCOVER_PROJECT")
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

    return {
        "provider": "openspec" if supported else None,
        "schema": schema,
        "supported": supported,
        "capabilities": sorted(set(capabilities)),
        "diagnostics": [] if supported else ["NO_PROVIDER_FOUND"],
    }


def normalize(project_root: Path) -> dict[str, Any]:
    probe_result = probe(project_root)
    if not probe_result["supported"]:
        return {"probe": probe_result, "current": {}, "proposed": {"changes": []}}

    openspec = project_root / "openspec"

    current_requirements: list[Requirement] = []
    specs_root = openspec / "specs"
    if specs_root.exists():
        for spec_file in sorted(specs_root.glob("*/spec.md")):
            spec_key = spec_file.parent.name
            current_requirements.extend(
                parse_requirements(
                    spec_file,
                    project_root,
                    spec_key=spec_key,
                    temporal_state="CURRENT",
                )
            )

    changes: list[Change] = []
    changes_root = openspec / "changes"
    if changes_root.exists():
        for change_dir in sorted(
            path
            for path in changes_root.iterdir()
            if path.is_dir() and path.name != "archive"
        ):
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
                            temporal_state="PROPOSED",
                            change=change_dir.name,
                            delta_mode=True,
                        )
                    )

            proposal = change_dir / "proposal.md"
            design = change_dir / "design.md"
            tasks = change_dir / "tasks.md"
            changes.append(
                Change(
                    key=change_dir.name,
                    temporal_state="PROPOSED",
                    proposal_path=_rel(proposal, project_root) if proposal.exists() else None,
                    design_path=_rel(design, project_root) if design.exists() else None,
                    tasks_path=_rel(tasks, project_root) if tasks.exists() else None,
                    task_count=count_tasks(tasks),
                    design_decision_count=count_design_decisions(design),
                    requirements=change_requirements,
                )
            )

    return {
        "probe": probe_result,
        "current": {
            "specifications": len({r.key.split("/")[0] for r in current_requirements}),
            "requirements": [asdict(r) for r in current_requirements],
        },
        "proposed": {
            "changes": [
                {
                    **{key: value for key, value in asdict(change).items() if key != "requirements"},
                    "requirements": [asdict(requirement) for requirement in change.requirements],
                }
                for change in changes
            ]
        },
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
